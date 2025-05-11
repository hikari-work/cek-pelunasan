package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.RepaymentService;
import org.cekpelunasan.service.UserService;
import org.cekpelunasan.utils.SystemUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class StatusCommandHandler implements CommandProcessor {

	private final RepaymentService repaymentService;
	private final UserService userService;
	private final BillService billService;

	public StatusCommandHandler(RepaymentService repaymentService, UserService userService, BillService billService) {
		this.repaymentService = repaymentService;
		this.userService = userService;
		this.billService = billService;
	}

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
		CompletableFuture<Repayment> latestRepaymentFuture = CompletableFuture.supplyAsync(repaymentService::findAll);
		CompletableFuture<Long> totalUsersFuture = CompletableFuture.supplyAsync(userService::countUsers);
		CompletableFuture<Integer> totalRepaymentsFuture = CompletableFuture.supplyAsync(repaymentService::countAll);
		CompletableFuture<String> systemLoadFuture = CompletableFuture.supplyAsync(() -> new SystemUtils().getSystemUtils());

		return CompletableFuture.allOf(latestRepaymentFuture, totalUsersFuture, totalRepaymentsFuture, systemLoadFuture)
			.thenComposeAsync(aVoid -> {
				try {
					Repayment latestRepayment = latestRepaymentFuture.get();
					Long totalUsers = totalUsersFuture.get();
					int totalRepayments = totalRepaymentsFuture.get();
					String systemLoad = systemLoadFuture.get();
					long executionTime = System.currentTimeMillis() - startTime;
					long billTotal = billCount.get();

					String statusMessage = buildStatusMessage(latestRepayment,
						totalUsers,
						totalRepayments,
						billTotal,
						systemLoad,
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
									  long totalBills,
									  String systemLoad,
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
			totalBills,
			systemLoad,
			latest.getCreatedAt().toString(),
			executionTime
		);
	}
}