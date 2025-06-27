package org.cekpelunasan.handler.callback.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.entity.Savings;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.service.repayment.RepaymentService;
import org.cekpelunasan.service.savings.SavingsService;
import org.cekpelunasan.utils.PenaltyUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DirectServicesHandler implements CallbackProcessor {
	private final RepaymentService repaymentService;
	private final SavingsService savingsService;


	@Override
	public String getCallBackData() {
		return "services";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			log.info("Direct Request Successfully Processed with query {}", update.getCallbackQuery().getData());
			String[] data = update.getCallbackQuery().getData().split("_");
			String query = data[1];
			String dataMessage = data[2];
			if (query.equals("Pelunasan")) {
				log.info("Sending Pelunasan Callback");
				processPelunasan(dataMessage, update.getCallbackQuery().getId(), telegramClient);
			} else if (query.equals("Tabungan")) {
				log.info("Sending Tabungan Callback");
				processTabungan(dataMessage, update.getCallbackQuery().getId(), telegramClient);
			}
		});
	}

	private void processTabungan(String query, String callbackId, TelegramClient telegramClient) {
		try {
			AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery.builder()
				.text(savingData(query))
				.callbackQueryId(callbackId)
				.showAlert(true)
				.build();
			telegramClient.execute(answerCallbackQuery);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void processPelunasan(String query, String callbackId, TelegramClient telegramClient) {
		log.info("Data Message is {}", query);
		Repayment repayment = repaymentService.findRepaymentById(Long.parseLong(query));

		String message = (repayment == null)
			? "❌ Data pelunasan tidak ditemukan"
			: calculate(repayment, new PenaltyUtils().penalty(
			repayment.getStartDate(),
			repayment.getPenaltyLoan(),
			repayment.getProduct(),
			repayment
		));
		if (repayment != null) {
			log.info("Data is Found");
		}
		try {
			telegramClient.execute(AnswerCallbackQuery.builder()
				.showAlert(true)
				.text(message)
				.callbackQueryId(callbackId)
				.build());
		} catch (TelegramApiException e) {
			log.error("Failed to send callback: ");
		}
	}

	public String calculate(Repayment repayment, Map<String, Long> penaltyMap) {
		Long bakidebet = repayment.getAmount();
		Long tunggakan = repayment.getInterest();
		Long denda = repayment.getPenaltyRepayment();
		Long total = bakidebet + tunggakan + denda + penaltyMap.get("penalty");

		return String.format("""
				SPK  : %s
				Nama : %s
				BD   : %s
				TG   : %s
				B+%s : %s
				DD   : %s
				TTL  : %s
				""",
			formatText(repayment.getCustomerId()),
			formatText(repayment.getName()),
			formatRupiah(bakidebet),
			formatRupiah(tunggakan),
			penaltyMap.get("multiplier"),
			formatRupiah(penaltyMap.get("penalty")),
			formatRupiah(denda),
			formatRupiah(total)
		);
	}

	private String formatRupiah(Long amount) {
		if (amount == null) return "Rp0";
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setGroupingSeparator('.');
		symbols.setDecimalSeparator(',');
		DecimalFormat df = new DecimalFormat("Rp#,##0", symbols);
		return df.format(amount);
	}

	private String formatText(String text) {
		return text == null ? "-" : text;
	}

	private String savingData(String query) {
		log.info("Fetching savings data for tabId: {}", query);
		try {
			Optional<Savings> byId = savingsService.findById(query);
			String message;
			if (byId.isEmpty()) {
				log.warn("No savings data found for tabId: {}", query);
				message = "❌ Data tabungan tidak ditemukan";
			} else {
				Savings savings = byId.get();
				log.info("Savings data found for tabId: {}, name: {}", query, savings.getName());

				// Calculate values with null checks to avoid NullPointerException
				long balance = savings.getBalance() != null ? savings.getBalance().longValue() : 0;
				long transaction = savings.getTransaction() != null ? savings.getTransaction().longValue() : 0;
				long minimumBalance = savings.getMinimumBalance() != null ? savings.getMinimumBalance().longValue() : 0;
				long blockingBalance = savings.getBlockingBalance() != null ? savings.getBlockingBalance().longValue() : 0;

				long book = balance + transaction;
				long effect = book - blockingBalance - minimumBalance;

				log.debug("Calculated values - Book: {}, Effect: {}, Block: {}",
					book, effect, blockingBalance);

				message = String.format("""
						NoRek  : %s
						Nama   : %s
						Buku   : %s
						Efek   : %s
						Block  : %s
						""",
					formatText(savings.getTabId()),
					formatText(savings.getName()),
					formatRupiah(book),
					formatRupiah(effect),
					formatRupiah(blockingBalance)
				);
				log.debug("Formatted savings message successfully");
			}
			return message;
		} catch (Exception e) {
			log.error("Error fetching savings data for tabId: {}: {}", query, e.getMessage(), e);
			return "❌ Error: Gagal memproses permintaan tabungan";
		}
	}
}