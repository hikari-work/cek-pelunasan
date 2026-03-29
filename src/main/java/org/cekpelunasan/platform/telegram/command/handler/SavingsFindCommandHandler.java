package org.cekpelunasan.platform.telegram.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationSavingsButton;
import org.cekpelunasan.platform.telegram.callback.pagination.SelectSavingsBranch;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.SavingsUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class SavingsFindCommandHandler extends AbstractCommandHandler {

	private final SavingsService savingsService;
	private final SelectSavingsBranch selectSavingsBranch;
	private final UserService userService;
	private final PaginationSavingsButton paginationSavingsButton;
	private final SavingsUtils savingsUtils;

	@Override
	public String getCommand() {
		return "/tab";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String name = text.replace("/tab ", "").trim();
			if (name.isEmpty() || name.equals("/tab")) {
				sendMessage(chatId, "Nama Harus Diisi", telegramClient);
				return;
			}
			String userBranch = userService.findUserBranch(chatId);
			if (userBranch == null) {
				Set<String> branches = savingsService.listAllBranch(name);
				if (branches.isEmpty()) {
					sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
					return;
				}
				sendMessage(chatId, "Data ditemukan dalam beberapa cabang", selectSavingsBranch.dynamicSelectBranch(branches, name), telegramClient);
				return;
			}
			Page<Savings> byNameAndBranch = savingsService.findByNameAndBranch(name, userBranch, 0);
			if (byNameAndBranch.isEmpty()) {
				sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
				return;
			}
			sendMessage(chatId,
				"Data ditemukan dalam beberapa cabang\n" + savingsUtils.buildMessage(byNameAndBranch, 0, System.currentTimeMillis()),
				paginationSavingsButton.keyboardMarkup(byNameAndBranch, userBranch, 0, name),
				telegramClient);
		});
	}
}
