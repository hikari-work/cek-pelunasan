package org.cekpelunasan.platform.whatsapp.service.hotkolek;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.service.bill.HotKolekService;
import org.cekpelunasan.platform.whatsapp.service.sender.WhatsAppSenderService;
import org.cekpelunasan.platform.whatsapp.service.utils.HotKolekMessageGenerator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Menangani perintah hot koleksi yang dikirim via WhatsApp.
 * <p>
 * Ketika AO (Account Officer) mengetik nomor SPK (12 digit), bot membaca
 * perintah itu sebagai tanda bahwa tagihan tersebut sudah dibayar hari ini.
 * Bot kemudian menyimpan data pembayaran ke database, lalu langsung mengirim
 * rekap daftar hot koleksi terbaru ke chat yang sama — baik personal maupun grup.
 * </p>
 * <p>
 * Format perintah yang dikenali: ".010600001234" atau bisa beberapa SPK sekaligus
 * dipisah spasi, contoh: ".010600001234 010600005678".
 * Bot juga mengirim reaksi emoji ke pesan asli sebagai tanda pesan sudah diproses.
 * </p>
 * <p>
 * Rekap yang dibuat mencakup tiga kios: Kaligondang, Kalikajar, dan Kejobong,
 * masing-masing dengan kategori minimal bayar, angsuran pertama, dan jatuh tempo.
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HandleKolekCommand {

	private final HotKolekService hotKolekService;
	private final WhatsAppSenderService whatsAppSenderService;
	private static final String COMMAND_PREFIX = ".";
	private static final String HOT_KOLEK_PATTERN = "^" + COMMAND_PREFIX + "\\d{12}(?:\\s\\d{12})*$";
	private static final String SPK_NUMBER_PATTERN = "^\\d{12}$";
	private static final String GROUP_SUFFIX = "@g.us";
	private static final String INDIVIDUAL_SUFFIX = "@s.whatsapp.net";

	private static final List<KiosConfig> KIOS_CONFIGS = List.of(
		new KiosConfig("1075", ""),
		new KiosConfig("KLJ", "KLJ"),
		new KiosConfig("KJB", "KJB")
	);
	private final HotKolekMessageGenerator hotKolekMessageGenerator;

	/**
	 * Memproses perintah hot kolek dari pesan WhatsApp yang masuk.
	 * <p>
	 * Alurnya: validasi format perintah → kirim reaksi emoji ke pesan asli →
	 * ekstrak nomor SPK → simpan tagihan yang sudah dibayar → ambil data rekap
	 * terbaru dari semua kios → kirim rekap ke chat.
	 * </p>
	 *
	 * @param command data webhook dari pesan WhatsApp yang berisi perintah hot kolek
	 */
	public void handleKolekCommand(WhatsAppWebhookDTO command) {
		String messageText = command.getPayload().getBody();
		if (!isValidHotKolekCommand(messageText)) {
			log.debug("Invalid hot kolek command received: {}", messageText);
			return;
		}

		log.info("Processing hot kolek command: {}", messageText);
		sendReactionAsync(command);

		List<String> spkNumbers = extractSpkNumbers(messageText);
		log.info("SPK Number is {}", spkNumbers);
		String chatId = buildChatId(command);

		savePayedBills(spkNumbers)
			.then(generateHotKolekMessage())
			.flatMap(message -> whatsAppSenderService.sendWhatsAppText(chatId, message))
			.subscribeOn(Schedulers.boundedElastic())
			.subscribe(
				ok -> log.debug("Hot kolek message sent successfully"),
				e -> log.error("Error processing hot kolek command", e)
			);
	}

	private void sendReactionAsync(WhatsAppWebhookDTO command) {
		Mono.delay(java.time.Duration.ofMillis(2000L))
			.then(Mono.fromSupplier(() -> {
				String chatId = buildChatId(command);
				String messageId = command.getPayload().getId();
				return whatsAppSenderService.sendReactionToMessage(chatId, messageId);
			}))
			.flatMap(mono -> mono)
			.subscribe(
				dto -> log.debug("Successfully sent reaction: {}", dto),
				e -> log.warn("Failed to send reaction: {}", e.getMessage())
			);
	}

	private Mono<String> generateHotKolekMessage() {
		List<Mono<BillsData>> dataMonos = KIOS_CONFIGS.stream()
			.map(this::fetchBillsData)
			.toList();
		return Mono.zip(dataMonos, arr -> {
			List<BillsData> results = Arrays.stream(arr)
				.map(o -> (BillsData) o)
				.toList();
			return hotKolekMessageGenerator.generateMessage(getLocationBills(results));
		});
	}

	private Mono<BillsData> fetchBillsData(KiosConfig config) {
		return Mono.zip(
			hotKolekService.findMinimalPay(config.code()),
			hotKolekService.findFirstPay(config.code()),
			hotKolekService.findDueDate(config.code())
		)
		.map(t -> new BillsData(t.getT1(), t.getT2(), t.getT3()))
		.subscribeOn(Schedulers.boundedElastic());
	}

	private Mono<Void> savePayedBills(List<String> spkNumbers) {
		if (spkNumbers == null || spkNumbers.isEmpty()) {
			log.debug("No SPK numbers to save");
			return Mono.empty();
		}
		return hotKolekService.saveAllPaying(spkNumbers)
			.doOnSuccess(v -> log.info("Successfully saved {} paid bills", spkNumbers.size()))
			.doOnError(e -> log.error("Error saving paid bills: {}", spkNumbers, e))
			.onErrorResume(e -> Mono.empty());
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
			new HotKolekMessageGenerator.CategoryBills("Jatuh tempo", billsData.dueDate())
		);
		return new HotKolekMessageGenerator.LocationBills(location, categoryBills);
	}

	private boolean isValidHotKolekCommand(String text) {
		if (text == null || text.trim().isEmpty()) {
			return false;
		}
		log.info("Valid: {} is {}", text, Pattern.matches(HOT_KOLEK_PATTERN, text.trim()));
		return Pattern.matches(HOT_KOLEK_PATTERN, text.trim());
	}

	private List<String> extractSpkNumbers(String messageText) {
		List<String> spkNumbers = new ArrayList<>();
		String[] tokens = messageText.trim().split("\\s+");
		for (String token : tokens) {
			String cleanToken = removeCommandPrefix(token);
			if (cleanToken.matches(SPK_NUMBER_PATTERN)) {
				spkNumbers.add(cleanToken);
			}
		}
		log.debug("Extracted {} SPK numbers from message", spkNumbers.size());
		return spkNumbers;
	}

	private String removeCommandPrefix(String token) {
		return token.startsWith(COMMAND_PREFIX) ? token.substring(1) : token;
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
			if (code == null) code = "";
			kiosFilter = kiosFilter == null ? "" : kiosFilter;
		}
	}

	private record BillsData(List<Bills> minimalPay, List<Bills> firstPay, List<Bills> dueDate) {
		public BillsData {
			minimalPay = minimalPay == null ? List.of() : List.copyOf(minimalPay);
			firstPay = firstPay == null ? List.of() : List.copyOf(firstPay);
			dueDate = dueDate == null ? List.of() : List.copyOf(dueDate);
		}
	}
}
