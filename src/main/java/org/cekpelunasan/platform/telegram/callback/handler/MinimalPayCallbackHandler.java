package org.cekpelunasan.platform.telegram.callback.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationToMinimalPay;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.MinimalPayUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinimalPayCallbackHandler extends AbstractCallbackHandler {

	private final PaginationToMinimalPay paginationToMinimalPay;
	private final BillService billService;
	private final UserService userService;
	private final MinimalPayUtils minimalPayUtils;


	@Override
	public String getCallBackData() {
		return "minimal";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			long chatId = update.getCallbackQuery().getMessage().getChatId();
			String[] data = update.getCallbackQuery().getData().split("_");

			int page = Integer.parseInt(data[2]);
			log.info("Bills Callback Received...");

			Optional<User> userOpt = userService.findUserByChatId(chatId);
			if (userOpt.isEmpty()) {
				log.info("User ID {} not Valid", chatId);
				sendMessage(chatId, "❌ *User tidak ditemukan*", telegramClient);
				return;
			}

			User user = userOpt.get();
			String userCode = user.getUserCode();

			Page<Bills> bills = null;
			if (user.getRoles() != null) {
				log.info("Finding Minimal Pay of {}", userCode);
				bills = switch (user.getRoles()) {
					case AO -> billService.findMinimalPaymentByAccountOfficer(userCode, page, 5);
					case PIMP, ADMIN -> billService.findMinimalPaymentByBranch(userCode, page, 5);
				};
			}

			if (bills != null && bills.isEmpty()) {
				log.info("Minimal Pay Is Empty....");
				sendMessage(chatId, "❌ *Tidak ada tagihan dengan minimal bayar tersisa.*", telegramClient);
				return;
			}

			StringBuilder message = new StringBuilder("📋 *Daftar Tagihan Minimal Bayar:*\n\n");
			if (bills != null) {
				for (Bills bill : bills) {
					message.append(minimalPayUtils.minimalPay(bill));
				}
			}

			InlineKeyboardMarkup markup = paginationToMinimalPay.dynamicButtonName(bills, page, userCode);
			editMessageWithMarkup(chatId, (int) update.getCallbackQuery().getMessage().getMessageId(), message.toString(), telegramClient, markup);
		});
	}

}