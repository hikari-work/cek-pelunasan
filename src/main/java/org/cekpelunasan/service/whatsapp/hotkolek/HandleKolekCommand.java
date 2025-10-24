package org.cekpelunasan.service.whatsapp.hotkolek;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.dto.whatsapp.send.GenericResponseDTO;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.service.Bill.HotKolekService;
import org.cekpelunasan.service.whatsapp.sender.WhatsAppSenderService;
import org.cekpelunasan.service.whatsapp.utils.HotKolekMessageGenerator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class HandleKolekCommand {

	private final HotKolekService hotKolekService;
	private final WhatsAppSenderService whatsAppSenderService;
	private static final String COMMAND_PREFIX = ".";
	private static final String HOT_KOLEK_PATTERN = "^\\.\\d{12}(?:\\s\\d{12})*$";
	private static final String SPK_NUMBER_PATTERN = "^\\d{12}$";
	private static final String GROUP_SUFFIX = "@g.us";
	private static final String INDIVIDUAL_SUFFIX = "@s.whatsapp.net";

	private static final List<KiosConfig> KIOS_CONFIGS = List.of(
		new KiosConfig("1075", ""),
		new KiosConfig("1172", "KLJ"),
		new KiosConfig("1173", "KJB")
	);
	private final HotKolekMessageGenerator hotKolekMessageGenerator;

	@Async
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> handleKolekCommand(WhatsAppWebhookDTO command) {
		return CompletableFuture.runAsync(() -> processKolekCommand(command));
	}

	private void processKolekCommand(WhatsAppWebhookDTO command) {
		String messageText = command.getMessage().getText();

		if (!isValidHotKolekCommand(messageText)) {
			log.debug("Invalid hot kolek command received: {}", messageText);
			return;
		}

		log.info("Processing hot kolek command: {}", messageText);

		sendReactionAsync(command);

		List<String> spkNumbers = extractSpkNumbers(messageText);
		savePayedBills(spkNumbers);

		String responseMessage = generateHotKolekMessage();
		String chatId = buildChatId(command);
		GenericResponseDTO genericResponseDTO = whatsAppSenderService.sendWhatsAppText(chatId, "Noted");

		whatsAppSenderService.sendWhatsAppText(chatId, responseMessage);
	}

	private void sendReactionAsync(WhatsAppWebhookDTO command) {
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(2000L);
				String chatId = buildChatId(command);
				String messageId = command.getMessage().getId();
				GenericResponseDTO genericResponseDTO = whatsAppSenderService.sendReactionToMessage(chatId, messageId);
				log.debug("Successfully sent reaction: {}", genericResponseDTO);
			} catch (Exception e) {
				log.warn("Failed to send reaction: {}", e.getMessage());
			}
		});
	}

	private boolean isValidHotKolekCommand(String text) {
		if (text == null || text.trim().isEmpty()) {
			return false;
		}
		return Pattern.matches(HOT_KOLEK_PATTERN, text.trim());
	}

	private List<String> extractSpkNumbers(String messageText) {
		List<String> spkNumbers = new ArrayList<>();
		String[] tokens = messageText.trim().split("\\s+");

		for (String token : tokens) {
			String cleanToken = removeCommandPrefix(token);
			if (isValidSpkNumber(cleanToken)) {
				spkNumbers.add(cleanToken);
			}
		}

		log.debug("Extracted {} SPK numbers from message", spkNumbers.size());
		return spkNumbers;
	}

	private String removeCommandPrefix(String token) {
		return token.startsWith(COMMAND_PREFIX) ? token.substring(1) : token;
	}

	private boolean isValidSpkNumber(String token) {
		return token.matches(SPK_NUMBER_PATTERN);
	}

	private String generateHotKolekMessage() {
		try {
			List<CompletableFuture<BillsData>> futures = KIOS_CONFIGS.stream()
				.map(this::fetchBillsDataAsync)
				.toList();

			return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
				.thenApply(v -> buildMessageFromResults(futures))
				.join();

		} catch (Exception e) {
			log.error("Error generating hot kolek message", e);
			return "Terjadi kesalahan saat memproses data. Silakan coba lagi.";
		}
	}

	private String buildMessageFromResults(List<CompletableFuture<BillsData>> futures) {
		List<BillsData> results = futures.stream()
			.map(CompletableFuture::join)
			.toList();

		if (results.size() != 3) {
			throw new IllegalStateException("Expected 3 results, got " + results.size());
		}
		return hotKolekMessageGenerator.generateMessage(getLocationBills(results));
	}

	private CompletableFuture<BillsData> fetchBillsDataAsync(KiosConfig config) {
		CompletableFuture<List<Bills>> minimalPayFuture = CompletableFuture
			.supplyAsync(() -> getBillsByType(config, BillType.MINIMAL_PAY));

		CompletableFuture<List<Bills>> firstPayFuture = CompletableFuture
			.supplyAsync(() -> getBillsByType(config, BillType.FIRST_PAY));

		CompletableFuture<List<Bills>> dueDateFuture = CompletableFuture
			.supplyAsync(() -> getBillsByType(config, BillType.DUE_DATE));

		return CompletableFuture.allOf(minimalPayFuture, firstPayFuture, dueDateFuture)
			.thenApply(v -> new BillsData(
				minimalPayFuture.join(),
				firstPayFuture.join(),
				dueDateFuture.join()
			));
	}

	private List<HotKolekMessageGenerator.LocationBills> getLocationBills(List<BillsData> billsData) {
		return List.of(
			createLocationBills("Kaligondang", billsData.get(0)),
			createLocationBills("Kalikajar", billsData.get(1)),
			createLocationBills("Kejobong", billsData.get(2))
		);
	}
	private HotKolekMessageGenerator.LocationBills createLocationBills(String location, BillsData billsData) {
		List<HotKolekMessageGenerator.CategoryBills> categoryBills = List.of(
			new HotKolekMessageGenerator.CategoryBills("", billsData.minimalPay()),
			new HotKolekMessageGenerator.CategoryBills("Angsuran Pertama", billsData.firstPay()),
			new HotKolekMessageGenerator.CategoryBills("Jatuh tempo", billsData.dueDate()
		));
		return new HotKolekMessageGenerator.LocationBills(location, categoryBills);
	}

	private List<Bills> getBillsByType(KiosConfig config, BillType billType) {
		try {
			List<Bills> bills = switch (billType) {
				case MINIMAL_PAY -> hotKolekService.findMinimalPay(config.code());
				case FIRST_PAY -> hotKolekService.findFirstPay(config.code());
				case DUE_DATE -> hotKolekService.findDueDate(config.code());
			};

			return filterBillsByKios(bills, config.kiosFilter());
		} catch (Exception e) {
			log.error("Error fetching bills for config {} and type {}", config, billType, e);
			return List.of();
		}
	}

	private List<Bills> filterBillsByKios(List<Bills> bills, String kiosFilter) {
		if (bills == null || bills.isEmpty()) {
			return List.of();
		}

		if (kiosFilter == null || kiosFilter.trim().isEmpty()) {
			return bills;
		}

		return bills.stream()
			.filter(bill -> bill != null && kiosFilter.equals(bill.getKios()))
			.toList();
	}

	private void savePayedBills(List<String> spkNumbers) {
		if (spkNumbers == null || spkNumbers.isEmpty()) {
			log.debug("No SPK numbers to save");
			return;
		}

		try {
			hotKolekService.saveAllPaying(spkNumbers);
			log.info("Successfully saved {} paid bills", spkNumbers.size());
		} catch (Exception e) {
			log.error("Error saving paid bills: {}", spkNumbers, e);
		}
	}

	private String buildChatId(WhatsAppWebhookDTO dto) {
		if (dto == null || dto.getCleanChatId() == null) {
			throw new IllegalArgumentException("Invalid WhatsApp DTO or clean chat ID");
		}

		String suffix = dto.isGroupChat() ? GROUP_SUFFIX : INDIVIDUAL_SUFFIX;
		return dto.getCleanChatId() + suffix;
	}


	private record KiosConfig(String code, String kiosFilter) {
		public KiosConfig {
			if (code == null || code.trim().isEmpty()) {
				throw new IllegalArgumentException("Kios code cannot be null or empty");
			}
			kiosFilter = kiosFilter == null ? "" : kiosFilter;
		}
	}

	private record BillsData(
		List<Bills> minimalPay,
		List<Bills> firstPay,
		List<Bills> dueDate
	) {
		public BillsData {
			minimalPay = minimalPay == null ? List.of() : List.copyOf(minimalPay);
			firstPay = firstPay == null ? List.of() : List.copyOf(firstPay);
			dueDate = dueDate == null ? List.of() : List.copyOf(dueDate);
		}
	}

	private enum BillType {
		MINIMAL_PAY,
		FIRST_PAY,
		DUE_DATE
	}
}