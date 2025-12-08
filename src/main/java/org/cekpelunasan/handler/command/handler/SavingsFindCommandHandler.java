package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.entity.Savings;
import org.cekpelunasan.handler.callback.pagination.PaginationSavingsButton;
import org.cekpelunasan.handler.callback.pagination.SelectSavingsBranch;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.savings.SavingsService;
import org.cekpelunasan.service.telegram.TelegramMessageService;
import org.cekpelunasan.service.users.UserService;
import org.cekpelunasan.utils.SavingsUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class SavingsFindCommandHandler implements CommandProcessor {

	private final SavingsService savingsService;
	private final SelectSavingsBranch selectSavingsBranch;
	private final UserService userService;
 private final PaginationSavingsButton paginationSavingsButton;
 private final SavingsUtils savingsUtils;
 private final TelegramMessageService telegramMessageService;


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
		return CommandProcessor.super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            if (text.length() < 2) {
                telegramMessageService.sendText(chatId, "Nama Harus Diisi", telegramClient);
                return;
            }
            String name = text.replace("/tab ", "");
            if (name.isEmpty()) {
                telegramMessageService.sendText(chatId, "Nama Harus Diisi", telegramClient);
                return;
            }
            String userBranch = userService.findUserBranch(chatId);
            if (userBranch == null) {
                Set<String> branches = savingsService.listAllBranch(name);
                if (branches.isEmpty()) {
                    telegramMessageService.sendText(chatId, "❌ *Data tidak ditemukan*", telegramClient);
                    return;
                }
                log.info("Data ditemukan dalam beberapa cabang:");
                sendMessageWithBrachSelection(chatId, name, branches, telegramClient);
                return;
            }
            Page<Savings> byNameAndBranch = savingsService.findByNameAndBranch(name, userBranch, 0);
            if (byNameAndBranch.isEmpty()) {
                log.info("Branch Tab is Not Found...");
                telegramMessageService.sendText(chatId, "❌ *Data tidak ditemukan*", telegramClient);
                return;
            }
            InlineKeyboardMarkup markup = paginationSavingsButton.keyboardMarkup(byNameAndBranch, userBranch, 0, name);
            String s = savingsUtils.buildMessage(byNameAndBranch, 0, System.currentTimeMillis());
            telegramMessageService.sendTextWithKeyboard(chatId, "Data ditemukan dalam beberapa cabang\n" + s, markup, telegramClient);
        });
    }


    private void sendMessageWithBrachSelection(long chatId, String name, Set<String> branches, TelegramClient telegramClient) {
        InlineKeyboardMarkup markup = selectSavingsBranch.dynamicSelectBranch(branches, name);
        telegramMessageService.sendTextWithKeyboard(chatId, "Data ditemukan dalam beberapa cabang", markup, telegramClient);
    }
}
