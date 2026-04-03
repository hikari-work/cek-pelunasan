package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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



import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
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
	public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, SimpleTelegramClient client) {
		return CompletableFuture.runAsync(() -> {
			try {
				String name = text.replace("/tab ", "").trim();
				if (name.isEmpty() || name.equals("/tab")) {
					sendMessage(chatId, "Nama Harus Diisi", client);
					return;
				}
				String userBranch = userService.findUserBranch(chatId).block();
				if (userBranch == null) {
					Set<String> branches = savingsService.listAllBranch(name).block();
					if (branches == null || branches.isEmpty()) {
						sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
						return;
					}
					sendMessage(chatId, "Data ditemukan dalam beberapa cabang, pilih cabang:", selectSavingsBranch.dynamicSelectBranch(branches, name), client);
					return;
				}
				Page<Savings> byNameAndBranch = savingsService.findByNameAndBranch(name, userBranch, 0).block();
				if (byNameAndBranch == null || byNameAndBranch.isEmpty()) {
					sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
					return;
				}
				sendMessage(chatId,
					savingsUtils.buildMessage(byNameAndBranch, 0, System.currentTimeMillis()),
					paginationSavingsButton.keyboardMarkup(byNameAndBranch, userBranch, 0, name),
					client);
			} catch (Exception e) {
				log.error("Error /tab chatId={} text='{}': {}", chatId, text, e.getMessage(), e);
				sendMessage(chatId, "❌ Terjadi kesalahan, coba lagi", client);
			}
		});
	}
}
