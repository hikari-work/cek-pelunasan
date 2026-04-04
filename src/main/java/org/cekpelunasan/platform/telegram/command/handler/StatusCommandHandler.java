package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.credithistory.CreditHistoryService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.SystemUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatusCommandHandler extends AbstractCommandHandler {

	private final UserService userService;
	private final BillService billService;
	private final CreditHistoryService creditHistoryService;

	@Override
	public String getCommand() {
		return "/status";
	}

	@Override
	public String getDescription() {
		return "Mengecek Status Server dan Database serta user terdaftar";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		long chatId = update.message.chatId;
		long startTime = System.currentTimeMillis();

		return Mono.zip(
				userService.countUsers(),
				creditHistoryService.countCreditHistory(),
				billService.countAllBills(),
				Mono.fromCallable(() -> new SystemUtils().getSystemUtils())
			)
			.flatMap(tuple -> {
				long executionTime = System.currentTimeMillis() - startTime;
				String statusMessage = buildStatusMessage(
					tuple.getT1(),
					tuple.getT2(),
					tuple.getT3(),
					tuple.getT4(),
					executionTime);
				return Mono.fromRunnable(() -> sendMessage(chatId, statusMessage, client));
			})
			.onErrorResume(e -> {
				log.error("Error processing status command", e);
				return Mono.fromRunnable(() -> sendMessage(chatId, "❌ Error mengambil data status. Silakan coba lagi.", client));
			})
			.then();
	}

	private String buildStatusMessage(long totalUsers, long credit, long totalBills, String systemLoad, long executionTime) {
		return String.format("""
                ⚡️ *PELUNASAN BOT STATUS*
                ╔══════════════════════
                ║ 🤖 Status: *ONLINE*
                ╠══════════════════════

                📊 *STATISTIK SISTEM*
                ┌────────────────────
                │ 👥 Users     : %d
                │ 📦 All Krd   : %d
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
			totalUsers, credit, totalBills, systemLoad, executionTime
		);
	}
}
