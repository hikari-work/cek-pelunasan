package org.cekpelunasan.service.whatsapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.dto.whatsapp.send.*;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.service.Bill.HotKolekService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WhatsAppCommandRouters {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppCommandRouters.class);
	private final GenerateMessageText generateMessageText;
	private final HotKolekService hotKolekService;
	private final WhatsAppSender whatsAppSender;
	private final ObjectMapper objectMapper;
	@Value("${admin.whatsapp}")
	private String adminWhatsapp;

	public boolean isWhatsappCommand(WhatsAppWebhookDTO dto) {
		return dto.getMessage().getText().startsWith(".");
	}


	public void whatsappCommandRouter(WhatsAppWebhookDTO dto) {
		if (dto.getMessage() == null) {
			return;
		}
		String text = dto.getMessage().getText();
		if (text == null) {
			return;
		}
		log.info("Received from={} id={}", dto.getCleanChatId(), dto.getMessage().getId());

		if (!isWhatsappCommand(dto)) {
			return;
		}

		boolean isGroupChat = dto.isGroupChat();

		if (text.startsWith(".flush")) {
			flushKolek(dto);
		} else {
			String pattern = "^\\.\\d{12}(?:\\s\\d{12})*$";
			Pattern pattern1 = Pattern.compile(pattern);
			Matcher matcher = pattern1.matcher(text);
			if (matcher.matches()) {
				log.info("Valid Hot Kolek Service");
				log.info("Is Group Message {}", isGroupChat);
				sendHotKolekMessage(dto, isGroupChat);
			} else {
				log.info("Invalid Hot Kolek Service");
			}
		}
	}

	public void sendHotKolekMessage(WhatsAppWebhookDTO dto, boolean isGroup) {
		log.info("Generating Non Blocking Message");
		try {
			String chatId = isGroup ? dto.getCleanChatId() + "@g.us" : dto.getCleanChatId() + "@s.whatsapp.net";
			String text = dto.getMessage().getText();
			List<String> spkList = getAllSpk(text);
			hotKolekService.saveAllPaying(spkList);
			String messageText = generateNonBlocking();
			MessageReactionDTO reactionDTO = new MessageReactionDTO("🙏");
			reactionDTO.setMessageId(dto.getMessage().getId());
			reactionDTO.setPhone(chatId);
			String reaction = whatsAppSender.buildUrl(TypeMessage.REACTION);
			whatsAppSender.request(reaction, reactionDTO);
			SendTextMessageDTO sendTextMessageDTO = new SendTextMessageDTO();
			sendTextMessageDTO.setMessage(messageText);
			sendTextMessageDTO.setReplyMessageId(dto.getMessage().getId());
			sendTextMessageDTO.setPhone(chatId);

			String url = whatsAppSender.buildUrl(TypeMessage.TEXT);
			log.info(">>> SENDING TEXT via WhatsAppSender, from={} and pushname={}", text, dto.getPushname());
			ResponseEntity<GenericResponseDTO> response = whatsAppSender.request(url, sendTextMessageDTO);

			boolean success = whatsAppSender.isSuccess(response);
			if (success) {
				log.info("Sukses Sending Text ke {}", chatId);
			} else {
				log.warn("Gagal Sending Text ke {}", chatId);
			}

		} catch (Exception e) {
			log.error("Error in sendHotKolekMessage", e);
		}
	}

	private List<String> getAllSpk(String messageText) {
		List<String> results = new ArrayList<>();
		String[] tokens = messageText.trim().split("\\s+");
		for (String token : tokens) {
			String cleanToken = token.startsWith(".") ? token.substring(1) : token;
			if (cleanToken.matches("^\\d{12}$")) {
				results.add(cleanToken);
			}
		}
		return results;
	}

	public String generateNonBlocking() {
		List<KiosConfig> kiosConfigs = List.of(
			new KiosConfig("1075", ""),
			new KiosConfig("1172", "KLJ"),
			new KiosConfig("1173", "KJB")
		);

		List<CompletableFuture<BillsData>> futures = kiosConfigs.stream()
			.map(this::fetchBillsDataAsync)
			.toList();

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
			.thenApply(v -> {
				List<BillsData> results = futures.stream()
					.map(CompletableFuture::join)
					.toList();

				BillsData data1075 = results.get(0);
				BillsData data1172 = results.get(1);
				BillsData data1173 = results.get(2);

				return generateMessageText.generateMessageText(
					data1075.minimalPay(), data1075.firstPay(), data1075.dueDate(),
					data1172.minimalPay(), data1172.firstPay(), data1172.dueDate(),
					data1173.minimalPay(), data1173.firstPay(), data1173.dueDate()
				);
			})
			.join();
	}

	private CompletableFuture<BillsData> fetchBillsDataAsync(KiosConfig config) {
		String code = config.code();
		String kiosFilter = config.kiosFilter();

		CompletableFuture<List<Bills>> minimalPay = CompletableFuture
			.supplyAsync(() -> filterBills(hotKolekService.findMinimalPay(code), kiosFilter));

		CompletableFuture<List<Bills>> firstPay = CompletableFuture
			.supplyAsync(() -> filterBills(hotKolekService.findFirstPay(code), kiosFilter));

		CompletableFuture<List<Bills>> dueDate = CompletableFuture
			.supplyAsync(() -> filterBills(hotKolekService.findDueDate(code), kiosFilter));

		return CompletableFuture.allOf(minimalPay, firstPay, dueDate)
			.thenApply(v -> new BillsData(
				minimalPay.join(),
				firstPay.join(),
				dueDate.join()
			));
	}

	private List<Bills> filterBills(List<Bills> bills, String kiosFilter) {
		return bills.stream()
			.filter(bill -> bill.getKios().equals(kiosFilter))
			.toList();
	}

	// Helper classes
	private record KiosConfig(String code, String kiosFilter) {}

	private record BillsData(
		List<Bills> minimalPay,

		List<Bills> firstPay,
		List<Bills> dueDate
	) {}

	private void flushKolek(WhatsAppWebhookDTO dto) {
		boolean isGroupChat = dto.isGroupChat();
		String chatId = isGroupChat ? dto.getCleanChatId() + "@g.us" : dto.getCleanChatId() + "@s.whatsapp.net";
		String senderId = dto.getSenderId();
		boolean authorized = dto.getFrom().contains(adminWhatsapp);
		log.info("Received {}", dto.toString());
		log.info("Sender Id is {}", senderId);
		String quotedMessage = dto.getMessage().getQuotedMessage();
		if (quotedMessage == null || quotedMessage.isEmpty()) {
			log.info("Quoted Message Is Empty");
			if (authorized) {
				log.info("Admin Whatsapp");
				hotKolekService.deleteAllPaying();
				String messageId = dto.getMessage().getId();
				String text = "Hot Kolek Sudah di reset untuk bulan ini. Silahkan hapus untuk nasabah yang sudah aman...";
				MessageUpdateDTO update = new MessageUpdateDTO();
				update.setPhone(chatId);
				update.setMessageId(messageId);
				update.setMessage(text);
				String url = whatsAppSender.buildUrl(TypeMessage.UPDATE);
				ResponseEntity<GenericResponseDTO> response = whatsAppSender.request(url, update);
				log.info("Response is {} from server", response.getStatusCode());
				return;
			} else {
				log.info("Not Admin Whatsapp");
				SendTextMessageDTO sendTextMessageDTO = new SendTextMessageDTO();
				sendTextMessageDTO.setMessage("Anda Tidak Diizinkan");
				sendTextMessageDTO.setReplyMessageId(dto.getMessage().getId());
				sendTextMessageDTO.setPhone(chatId);
				String url = whatsAppSender.buildUrl(TypeMessage.TEXT);
				ResponseEntity<GenericResponseDTO> response = whatsAppSender.request(url, sendTextMessageDTO);
				log.info("Response is {}", response.getStatusCode());
				return;
			}
		} else {
			log.info("Quoted Message Is Not Empty");
			String text = dto.getMessage().getQuotedMessage();
			List<String> allSpk = getAllSpk(text);
			hotKolekService.deleteAllPaying(allSpk);
			SendTextMessageDTO sendTextMessageDTO = new SendTextMessageDTO();
			sendTextMessageDTO.setMessage("Data sudah dihapus");
			sendTextMessageDTO.setReplyMessageId(dto.getMessage().getId());
			sendTextMessageDTO.setPhone(chatId);
			String url = whatsAppSender.buildUrl(TypeMessage.TEXT);
			ResponseEntity<GenericResponseDTO> response = whatsAppSender.request(url, sendTextMessageDTO);

		}

	}

}
