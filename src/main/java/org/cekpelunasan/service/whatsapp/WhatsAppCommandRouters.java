package org.cekpelunasan.service.whatsapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.dto.whatsapp.send.*;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.service.Bill.HotKolekService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppCommandRouters {

	private static final String COMMAND_PREFIX = ".";
	private static final String HOT_KOLEK_PATTERN = "^\\.\\d{12}(?:\\s\\d{12})*$";
	private static final String FLUSH_COMMAND = ".flush";
	private static final String SPK_NUMBER_PATTERN = "^\\d{12}$";
	private static final String GROUP_SUFFIX = "@g.us";
	private static final String INDIVIDUAL_SUFFIX = "@s.whatsapp.net";

	private final GenerateMessageText generateMessageText;
	private final HotKolekService hotKolekService;
	private final WhatsAppSender whatsAppSender;

	@Value("${admin.whatsapp}")
	private String adminWhatsapp;

	public boolean isWhatsappCommand(WhatsAppWebhookDTO dto) {
		return dto.getMessage() != null
			&& dto.getMessage().getText() != null
			&& dto.getMessage().getText().startsWith(COMMAND_PREFIX);
	}

	public void whatsappCommandRouter(WhatsAppWebhookDTO dto) {
		if (!isValidMessage(dto)) {
			return;
		}

		String text = dto.getMessage().getText();
		log.info("Received command from={} id={}", dto.getCleanChatId(), dto.getMessage().getId());

		if (text.startsWith(FLUSH_COMMAND)) {
			handleFlushCommand(dto);
		} else if (isValidHotKolekCommand(text)) {
			log.info("Valid Hot Kolek Service, isGroup={}", dto.isGroupChat());
			handleHotKolekCommand(dto);
		} else {
			log.info("Invalid command format: {}", text);
		}
	}

	private boolean isValidMessage(WhatsAppWebhookDTO dto) {
		return dto.getMessage() != null && dto.getMessage().getText() != null;
	}

	private boolean isValidHotKolekCommand(String text) {
		return Pattern.matches(HOT_KOLEK_PATTERN, text);
	}

	private void handleHotKolekCommand(WhatsAppWebhookDTO dto) {
		try {
			String chatId = buildChatId(dto);
			String text = dto.getMessage().getText();

			List<String> spkList = extractSpkNumbers(text);
			hotKolekService.saveAllPaying(spkList);

			sendReaction(dto, chatId);
			sendHotKolekResponse(dto, chatId);

		} catch (Exception e) {
			log.error("Error in handleHotKolekCommand for chat: {}", dto.getCleanChatId(), e);
		}
	}

	private String buildChatId(WhatsAppWebhookDTO dto) {
		String suffix = dto.isGroupChat() ? GROUP_SUFFIX : INDIVIDUAL_SUFFIX;
		return dto.getCleanChatId() + suffix;
	}

	private List<String> extractSpkNumbers(String messageText) {
		List<String> spkNumbers = new ArrayList<>();
		String[] tokens = messageText.trim().split("\\s+");

		for (String token : tokens) {
			String cleanToken = token.startsWith(COMMAND_PREFIX) ? token.substring(1) : token;
			if (cleanToken.matches(SPK_NUMBER_PATTERN)) {
				spkNumbers.add(cleanToken);
			}
		}
		return spkNumbers;
	}

	private void sendReaction(WhatsAppWebhookDTO dto, String chatId) {
		try {
			MessageReactionDTO reactionDTO = new MessageReactionDTO("🙏");
			reactionDTO.setMessageId(dto.getMessage().getId());
			reactionDTO.setPhone(chatId);

			String reactionUrl = whatsAppSender.buildUrl(TypeMessage.REACTION);
			whatsAppSender.request(reactionUrl, reactionDTO);
		} catch (Exception e) {
			log.warn("Failed to send reaction to {}", chatId, e);
		}
	}

	private void sendHotKolekResponse(WhatsAppWebhookDTO dto, String chatId) {
		try {
			String messageText = generateHotKolekMessage();

			SendTextMessageDTO sendTextMessageDTO = new SendTextMessageDTO();
			sendTextMessageDTO.setMessage(messageText);
			sendTextMessageDTO.setReplyMessageId(dto.getMessage().getId());
			sendTextMessageDTO.setPhone(chatId);

			String url = whatsAppSender.buildUrl(TypeMessage.TEXT);
			ResponseEntity<GenericResponseDTO> response = whatsAppSender.request(url, sendTextMessageDTO);

			if (whatsAppSender.isSuccess(response)) {
				log.info("Successfully sent hot kolek message to {}", chatId);
			} else {
				log.warn("Failed to send hot kolek message to {}", chatId);
			}
		} catch (Exception e) {
			log.error("Error sending hot kolek response to {}", chatId, e);
		}
	}

	private String generateHotKolekMessage() {
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

				return generateMessageText.generateMessageText(
					results.get(0).minimalPay(), results.get(0).firstPay(), results.get(0).dueDate(),
					results.get(1).minimalPay(), results.get(1).firstPay(), results.get(1).dueDate(),
					results.get(2).minimalPay(), results.get(2).firstPay(), results.get(2).dueDate()
				);
			})
			.join();
	}

	private CompletableFuture<BillsData> fetchBillsDataAsync(KiosConfig config) {
		CompletableFuture<List<Bills>> minimalPay = CompletableFuture
			.supplyAsync(() -> filterBillsByKios(hotKolekService.findMinimalPay(config.code()), config.kiosFilter()));

		CompletableFuture<List<Bills>> firstPay = CompletableFuture
			.supplyAsync(() -> filterBillsByKios(hotKolekService.findFirstPay(config.code()), config.kiosFilter()));

		CompletableFuture<List<Bills>> dueDate = CompletableFuture
			.supplyAsync(() -> filterBillsByKios(hotKolekService.findDueDate(config.code()), config.kiosFilter()));

		return CompletableFuture.allOf(minimalPay, firstPay, dueDate)
			.thenApply(v -> new BillsData(minimalPay.join(), firstPay.join(), dueDate.join()));
	}

	private List<Bills> filterBillsByKios(List<Bills> bills, String kiosFilter) {
		return bills.stream()
			.filter(bill -> bill.getKios().equals(kiosFilter))
			.toList();
	}

	private void handleFlushCommand(WhatsAppWebhookDTO dto) {
		String chatId = buildChatId(dto);
		String quotedMessage = dto.getMessage().getQuotedMessage();

		if (isEmptyQuotedMessage(quotedMessage)) {
			handleFlushAllCommand(dto, chatId);
		} else {
			handleFlushSpecificCommand(dto, chatId, quotedMessage);
		}
	}

	private boolean isEmptyQuotedMessage(String quotedMessage) {
		return quotedMessage == null || quotedMessage.isEmpty();
	}

	private void handleFlushAllCommand(WhatsAppWebhookDTO dto, String chatId) {
		if (!isAuthorizedUser(dto)) {
			sendUnauthorizedMessage(dto, chatId);
			return;
		}

		try {
			hotKolekService.deleteAllPaying();
			updateMessageWithFlushConfirmation(dto, chatId);
			log.info("Admin {} successfully flushed all hot kolek data", dto.getSenderId());
		} catch (Exception e) {
			log.error("Error flushing all hot kolek data", e);
		}
	}

	private void handleFlushSpecificCommand(WhatsAppWebhookDTO dto, String chatId, String quotedMessage) {
		try {
			List<String> spkNumbers = extractSpkNumbers(quotedMessage);
			hotKolekService.deleteAllPaying(spkNumbers);
			sendTextMessage(dto, chatId, "Data sudah dihapus");
			log.info("Successfully deleted specific SPK numbers: {}", spkNumbers);
		} catch (Exception e) {
			log.error("Error flushing specific hot kolek data", e);
		}
	}

	private boolean isAuthorizedUser(WhatsAppWebhookDTO dto) {
		return dto.getFrom().contains(adminWhatsapp);
	}

	private void sendUnauthorizedMessage(WhatsAppWebhookDTO dto, String chatId) {
		sendTextMessage(dto, chatId, "Anda Tidak Diizinkan");
		log.warn("Unauthorized flush attempt from: {}", dto.getSenderId());
	}

	private void updateMessageWithFlushConfirmation(WhatsAppWebhookDTO dto, String chatId) {
		String confirmationText = "Hot Kolek Sudah di reset untuk bulan ini. Silahkan hapus untuk nasabah yang sudah aman...";

		MessageUpdateDTO update = new MessageUpdateDTO();
		update.setPhone(chatId);
		update.setMessageId(dto.getMessage().getId());
		update.setMessage(confirmationText);

		String url = whatsAppSender.buildUrl(TypeMessage.UPDATE);
		ResponseEntity<GenericResponseDTO> response = whatsAppSender.request(url, update);

		log.info("Flush confirmation update response: {}", response.getStatusCode());
	}

	private void sendTextMessage(WhatsAppWebhookDTO dto, String chatId, String message) {
		try {
			SendTextMessageDTO sendTextMessageDTO = new SendTextMessageDTO();
			sendTextMessageDTO.setMessage(message);
			sendTextMessageDTO.setReplyMessageId(dto.getMessage().getId());
			sendTextMessageDTO.setPhone(chatId);

			String url = whatsAppSender.buildUrl(TypeMessage.TEXT);
			ResponseEntity<GenericResponseDTO> response = whatsAppSender.request(url, sendTextMessageDTO);

			log.info("Text message sent with response: {}", response.getStatusCode());
		} catch (Exception e) {
			log.error("Error sending text message to {}", chatId, e);
		}
	}

	private record KiosConfig(String code, String kiosFilter) {}

	private record BillsData(List<Bills> minimalPay, List<Bills> firstPay, List<Bills> dueDate) {}
}