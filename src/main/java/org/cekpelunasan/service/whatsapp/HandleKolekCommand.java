package org.cekpelunasan.service.whatsapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.service.Bill.HotKolekService;
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

	private final GenerateMessageText generateMessageText;
	private static final String COMMAND_PREFIX = ".";
	private static final String HOT_KOLEK_PATTERN = "^\\.\\d{12}(?:\\s\\d{12})*$";

	private static final String SPK_NUMBER_PATTERN = "^\\d{12}$";

	private record KiosConfig(String code, String kiosFilter) {}

	private record BillsData(List<Bills> minimalPay, List<Bills> firstPay, List<Bills> dueDate) {}


	public CompletableFuture<Void> handleKolekCommand(WhatsAppWebhookDTO command) {
		return CompletableFuture.runAsync(() -> {
			if (!isValidHotKolekCommand(command.getMessage().getText())) {
				return;
			}
			String text = command.getMessage().getText();
			List<String> spkNumbers = extractSpkNumbers(text);
			savingPayedBills(spkNumbers);
			String message = generateHotKolekMessage();


		});

	}
	private boolean isValidHotKolekCommand(String text) {
		return Pattern.matches(HOT_KOLEK_PATTERN, text);
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

	private void savingPayedBills(List<String> spkNumbers) {
		hotKolekService.saveAllPaying(spkNumbers);
	}
}
