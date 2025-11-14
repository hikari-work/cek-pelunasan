package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.credithistory.CreditHistoryService;
import org.cekpelunasan.service.customerhistory.CustomerHistoryService;
import org.cekpelunasan.service.users.UserService;
import org.cekpelunasan.utils.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class StatusCommandHandler implements CommandProcessor {

	private static final Logger log = LoggerFactory.getLogger(StatusCommandHandler.class);

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
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		try {
			long chatId = update.getMessage().getChatId();
			long startTime = System.currentTimeMillis();

			log.info("Processing /status command for chat: {}", chatId);

			CompletableFuture<Long> billCount = CompletableFuture.supplyAsync(billService::countAllBills);
			CompletableFuture<Long> customerHistoryCount = CompletableFuture.supplyAsync(customerHistoryService::countCustomerHistory);
			CompletableFuture<Long> totalUsersFuture = CompletableFuture.supplyAsync(userService::countUsers);
			CompletableFuture<String> systemLoadFuture = CompletableFuture.supplyAsync(() -> new SystemUtils().getSystemUtils());
			CompletableFuture<Long> creditHistory = CompletableFuture.supplyAsync(creditHistoryService::countCreditHistory);

			CompletableFuture.allOf(
					totalUsersFuture,
					systemLoadFuture,
					creditHistory,
					customerHistoryCount,
					billCount)
				.thenRunAsync(() -> {
					try {
						Long totalUsers = totalUsersFuture.get();
						String systemLoad = systemLoadFuture.get();
						long executionTime = System.currentTimeMillis() - startTime;
						long billTotal = billCount.get();
						long creditHistoryTotal = creditHistory.get();
						long customerHistoryTotal = customerHistoryCount.get();

						String statusMessage = buildStatusMessage(
							totalUsers,
							creditHistoryTotal,
							billTotal,
							systemLoad,
							customerHistoryTotal,
							executionTime);

						sendMessage(chatId, statusMessage, telegramClient);
						log.info("Status message sent to chat: {}", chatId);

					} catch (Exception e) {
						log.error("Error processing status command", e);
						sendMessage(chatId, "❌ Error mengambil data status. Silakan coba lagi.", telegramClient);
					}
				}).join();

		} catch (Exception e) {
			log.error("Error in status command handler", e);
		}
		return null;
	}

	private String buildStatusMessage(long totalUsers,
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
                │ 📦 All Krd   : %d
                │ 📦 Cek CIF   : %d
                │ 💳 Tagihan   : %d
                │ ⚙️ Load      : %s
                └────────────────────
                
                📡 *INFORMASI SERVER*
                ┌────────────────────
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
			credit,
			customerHistoryTotal,
			totalBills,
			systemLoad,
			executionTime
		);
	}
}