package org.cekpelunasan.platform.telegram.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.credithistory.CreditHistoryService;
import org.cekpelunasan.core.service.customerhistory.CustomerHistoryService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.SystemUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatusCommandHandler extends AbstractCommandHandler {

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
		return "Mengecek Status Server dan Database serta user terdaftar";
	}

	@Override
	@Async
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		long chatId = update.getMessage().getChatId();
		long startTime = System.currentTimeMillis();

		CompletableFuture<Long> billCount = CompletableFuture.supplyAsync(billService::countAllBills);
		CompletableFuture<Long> customerHistoryCount = CompletableFuture.supplyAsync(customerHistoryService::countCustomerHistory);
		CompletableFuture<Long> totalUsersFuture = CompletableFuture.supplyAsync(userService::countUsers);
		CompletableFuture<String> systemLoadFuture = CompletableFuture.supplyAsync(() -> new SystemUtils().getSystemUtils());
		CompletableFuture<Long> creditHistory = CompletableFuture.supplyAsync(creditHistoryService::countCreditHistory);

		return CompletableFuture.allOf(totalUsersFuture, systemLoadFuture, creditHistory, customerHistoryCount, billCount)
			.thenRunAsync(() -> {
				try {
					long executionTime = System.currentTimeMillis() - startTime;
					String statusMessage = buildStatusMessage(
						totalUsersFuture.get(),
						creditHistory.get(),
						billCount.get(),
						systemLoadFuture.get(),
						customerHistoryCount.get(),
						executionTime);
					sendMessage(chatId, statusMessage, telegramClient);
				} catch (Exception e) {
					log.error("Error processing status command", e);
					sendMessage(chatId, "❌ Error mengambil data status. Silakan coba lagi.", telegramClient);
				}
			});
	}

	private String buildStatusMessage(long totalUsers, long credit, long totalBills, String systemLoad, long customerHistoryTotal, long executionTime) {
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
			totalUsers, credit, customerHistoryTotal, totalBills, systemLoad, executionTime
		);
	}
}
