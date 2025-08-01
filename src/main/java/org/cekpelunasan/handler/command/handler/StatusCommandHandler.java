package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.credithistory.CreditHistoryService;
import org.cekpelunasan.service.customerhistory.CustomerHistoryService;
import org.cekpelunasan.service.repayment.RepaymentService;
import org.cekpelunasan.service.users.UserService;
import org.cekpelunasan.utils.SystemUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class StatusCommandHandler implements CommandProcessor {

	private final RepaymentService repaymentService;
	private final UserService userService;
	private final BillService billService;
	private final CreditHistoryService creditHistoryService;
	private final CustomerHistoryService customerHistoryService;


	@Override
	public String getCommand() {
		return "/status";
	}

	@Override
	public String getDescription() {
		return """
			Mengecek Status Server dan Database
			serta user terdaftar
			""";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		long startTime = System.currentTimeMillis();

		CompletableFuture<Long> billCount = CompletableFuture.supplyAsync(billService::countAllBills);
		CompletableFuture<Long> customerHistoryCount = CompletableFuture.supplyAsync(customerHistoryService::countCustomerHistory);
		CompletableFuture<Repayment> latestRepaymentFuture = CompletableFuture.supplyAsync(repaymentService::findAll);
		CompletableFuture<Long> totalUsersFuture = CompletableFuture.supplyAsync(userService::countUsers);
		CompletableFuture<Integer> totalRepaymentsFuture = CompletableFuture.supplyAsync(repaymentService::countAll);
		CompletableFuture<String> systemLoadFuture = CompletableFuture.supplyAsync(() -> new SystemUtils().getSystemUtils());
		CompletableFuture<Long> creditHistory = CompletableFuture.supplyAsync(creditHistoryService::countCreditHistory);



		return CompletableFuture.allOf(latestRepaymentFuture,
				totalUsersFuture,
				totalRepaymentsFuture,
				systemLoadFuture,
				creditHistory,
				customerHistoryCount)
			.thenComposeAsync(aVoid -> {
				try {
					Repayment latestRepayment = latestRepaymentFuture.get();
					Long totalUsers = totalUsersFuture.get();
					int totalRepayments = totalRepaymentsFuture.get();
					String systemLoad = systemLoadFuture.get();
					long executionTime = System.currentTimeMillis() - startTime;
					long billTotal = billCount.get();
					long creditHistoryTotal = creditHistory.get();
					long customerHistoryTotal = customerHistoryCount.get();

					String statusMessage = buildStatusMessage(latestRepayment,
						totalUsers,
						totalRepayments,
						creditHistoryTotal,
						billTotal,
						systemLoad,
						customerHistoryTotal,
						executionTime);
					sendMessage(chatId, statusMessage, telegramClient);
				} catch (Exception e) {
					log.error("Error Send Message");
				}
				return CompletableFuture.completedFuture(null);
			});
	}

	private String buildStatusMessage(Repayment latest,
									  long totalUsers,
									  int totalRepayments,
									  long credit,
									  long totalBills,
									  String systemLoad,
									  long customerHistoryTotal,
									  long executionTime) {
		return String.format("""
				⚡️ *PELUNASAN BOT STATUS*
				╔══════════════════════
				║ 🤖 Status: *ONLINE*
				╠══════════════════════
				
				📊 *STATISTIK SISTEM*
				┌────────────────────
				│ 👥 Users     : %d
				│ 📦 Pelunasan : %d
				│ 📦 All Krd   : %d
				│ 📦 Cek CIF   : %d
				│ 💳 Tagihan   : %d
				│ ⚙️ Load      : %s
				└────────────────────
				
				📡 *INFORMASI SERVER*
				┌────────────────────
				│ 🕒 Last Update: %s
				│ 🔋 Health     : 100%%
				└────────────────────
				
				🎯 *QUICK TIPS*
				┌────────────────────
				│ • Ketik /help untuk bantuan
				│ • Cek status setiap hari
				│ • Update data secara rutin
				└────────────────────
				
				✨ _System is healthy and ready!_
				⏱️ _Generated in %dms_
				""",
			totalUsers,
			totalRepayments,
			credit,
			customerHistoryTotal,
			totalBills,
			systemLoad,
			latest.getCreatedAt().toString(),
			executionTime
		);
	}
}