package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.Savings;
import org.cekpelunasan.handler.callback.pagination.PaginationSavingsButton;
import org.cekpelunasan.handler.callback.pagination.SelectSavingsBranch;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.savings.SavingsService;
import org.cekpelunasan.service.users.UserService;
import org.cekpelunasan.utils.SavingsUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
public class SavingsFindCommandHandler implements CommandProcessor {

	private final AuthorizedChats authorizedChats1;
	private final SavingsService savingsService;
	private final SelectSavingsBranch selectSavingsBranch;
	private final UserService userService;
	private final PaginationSavingsButton paginationSavingsButton;
	private final SavingsUtils savingsUtils;

	public SavingsFindCommandHandler(AuthorizedChats authorizedChats1, SavingsService savingsService, SelectSavingsBranch selectSavingsBranch, UserService userService, PaginationSavingsButton paginationSavingsButton, SavingsUtils savingsUtils) {
		this.authorizedChats1 = authorizedChats1;
		this.savingsService = savingsService;
		this.selectSavingsBranch = selectSavingsBranch;
		this.userService = userService;
		this.paginationSavingsButton = paginationSavingsButton;
		this.savingsUtils = savingsUtils;
	}

	@Override
	public String getCommand() {
		return "/tab";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (!authorizedChats1.isAuthorized(chatId)) {
				sendMessage(chatId, "Unauthorized", telegramClient);
				return;
			}
			if (text.length() < 2) {
				sendMessage(chatId, "Nama Harus Diisi", telegramClient);
				return;
			}
			String name = text.replace("/tab ", "");
			if (name.isEmpty()) {
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
				log.info("Data ditemukan dalam beberapa cabang:");
				sendMessageWithBrachSelection(chatId, name, branches, telegramClient);
				return;
			}
			Page<Savings> byNameAndBranch = savingsService.findByNameAndBranch(name, userBranch, 0);
			if (byNameAndBranch.isEmpty()) {
				log.info("Branch Tab is Not Found...");
				sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
				return;
			}
			InlineKeyboardMarkup markup = paginationSavingsButton.keyboardMarkup(byNameAndBranch, userBranch, 0, name);
			String s = savingsUtils.buildMessage(byNameAndBranch, 0, System.currentTimeMillis());
			sendMessage(chatId, telegramClient, markup, s);
		});
	}


	private void sendMessageWithBrachSelection(long chatId, String name, Set<String> branches, TelegramClient telegramClient) {
		InlineKeyboardMarkup markup = selectSavingsBranch.dynamicSelectBranch(branches, name);
		sendMessage(chatId, telegramClient, markup);
	}

	private void sendMessage(Long chatId, TelegramClient telegramClient, InlineKeyboardMarkup markup) {
		try {
			telegramClient.execute(SendMessage.builder()
				.chatId(chatId.toString())
				.text("Data ditemukan dalam beberapa cabang")
				.parseMode("Markdown")
				.replyMarkup(markup)
				.build());
		} catch (Exception e) {
			log.error("❌ Gagal mengirim pesan ke chatId {}: {}", chatId, e.getMessage(), e);
		}
	}
	private void sendMessage(Long chatId, TelegramClient telegramClient, InlineKeyboardMarkup markup, String text) {
		try {
			telegramClient.execute(SendMessage.builder()
				.chatId(chatId.toString())
				.text("Data ditemukan dalam beberapa cabang\n" + text )
				.parseMode("Markdown")
				.replyMarkup(markup)
				.build());
		} catch (Exception e) {
			log.error("❌ Gagal mengirim pesan ke chatId {}: {}", chatId, e.getMessage(), e);
		}
	}
}
