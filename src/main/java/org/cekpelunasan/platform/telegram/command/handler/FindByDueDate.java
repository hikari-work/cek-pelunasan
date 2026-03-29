package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationBillsByNameCallbackHandler;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.DateUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;



import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class FindByDueDate extends AbstractCommandHandler {

	private final UserService userService;
	private final BillService billService;
	private final DateUtils dateUtils;

	@Override
	public String getCommand() {
		return "/jb";
	}

	@Override
	public String getDescription() {
		return "📅 *Cek tagihan jatuh tempo hari ini*.\nGunakan command ini untuk melihat daftar tagihan Anda yang jatuh tempo hari ini.";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, SimpleTelegramClient client) {
		return CompletableFuture.runAsync(() -> {
			Optional<User> userOpt = userService.findUserByChatId(chatId);
			if (userOpt.isEmpty()) {
				sendMessage(chatId, "❌ *User tidak ditemukan*", client);
				return;
			}

			User user = userOpt.get();
			String userCode = user.getUserCode();
			String today = dateUtils.converterDate(LocalDateTime.now());

			Page<Bills> billsPage = Page.empty();
			if (user.getRoles() != null) {
				billsPage = switch (user.getRoles()) {
					case AO -> billService.findDueDateByAccountOfficer(userCode, today, 0, 5);
					case PIMP -> billService.findBranchAndPayDown(userCode, today, 0, 5);
					default -> Page.empty();
				};
			}

			if (billsPage.isEmpty()) {
				sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
				return;
			}

			StringBuilder builder = new StringBuilder("Halaman 1 dari " + billsPage.getTotalPages() + "\n📋 *Daftar Tagihan Jatuh Tempo Hari Ini:*\n\n");
			billsPage.forEach(bills -> builder.append(messageBuilder(bills)));

			sendMessage(chatId, builder.toString(), new PaginationBillsByNameCallbackHandler().dynamicButtonName(billsPage, 0, userCode), client);
		});
	}

	private String messageBuilder(Bills bills) {
		return String.format("""
				👤 *%s*
				┌──────────────────────
				│ 📎 *INFORMASI KREDIT*
				│ ├─ 🔖 SPK      : `%s`
				│ ├─ 📍 Alamat   : %s
				│ └─ 📅 Jth Tempo: %s
				│
				│ 💰 *RINCIAN*
				│ ├─ 💸 Tagihan  : Rp %,d
				│ └─ 👨‍💼 AO       : %s
				└──────────────────────

				ℹ️ _Tap SPK untuk menyalin_
				""",
			bills.getName(),
			bills.getNoSpk(),
			bills.getAddress().length() > 30 ? bills.getAddress().substring(0, 27) + "..." : bills.getAddress(),
			bills.getPayDown() != null ? bills.getPayDown() : "Tidak tersedia",
			bills.getFullPayment(),
			bills.getAccountOfficer()
		);
	}
}
