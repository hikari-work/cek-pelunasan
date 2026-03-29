package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationToMinimalPay;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.MinimalPayUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;



import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class MinimalPayCommand extends AbstractCommandHandler {

	private final UserService userService;
	private final BillService billService;
	private final PaginationToMinimalPay paginationToMinimalPay;
	private final MinimalPayUtils minimalPayUtils;

	@Override
	public String getCommand() {
		return "/pabpr";
	}

	@Override
	public String getDescription() {
		return "Menampilkan daftar tagihan yang masih memiliki minimal bayar tersisa.";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.AO, AccountOfficerRoles.PIMP, AccountOfficerRoles.ADMIN})
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
			if (user.getRoles() == null) return;

			Page<Bills> bills = switch (user.getRoles()) {
				case AO -> billService.findMinimalPaymentByAccountOfficer(userCode, 0, 5);
				case PIMP, ADMIN -> billService.findMinimalPaymentByBranch(userCode, 0, 5);
			};

			if (bills.isEmpty()) {
				sendMessage(chatId, "❌ *Tidak ada tagihan dengan minimal bayar tersisa.*", client);
				return;
			}

			StringBuilder message = new StringBuilder("""
				📋 *DAFTAR TAGIHAN MINIMAL*
				═══════════════════════════

				""");
			bills.forEach(bill -> message.append(minimalPayUtils.minimalPay(bill)));
			message.append("""
				⚠️ *Catatan Penting*:
				▢ _Tap SPK untuk menyalin_
				▢ _Pembayaran harus dilakukan sebelum jatuh bayar_
				""");

			sendMessage(chatId, message.toString(), paginationToMinimalPay.dynamicButtonName(bills, 0, userCode), client);
		});
	}
}
