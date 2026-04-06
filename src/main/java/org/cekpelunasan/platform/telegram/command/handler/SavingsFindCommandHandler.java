package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationSavingsButton;
import org.cekpelunasan.platform.telegram.callback.pagination.SelectSavingsBranch;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.SavingsUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String name = text.replace("/tab ", "").trim();
		if (name.isEmpty() || name.equals("/tab")) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "Nama Harus Diisi", client));
		}
		return userService.findUserBranch(chatId)
			.flatMap(userBranch -> savingsService.findByNameAndBranch(name, userBranch, 0)
				.flatMap(byNameAndBranch -> Mono.fromRunnable(() -> {
					if (byNameAndBranch.isEmpty()) {
						sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
						return;
					}
					sendMessage(chatId,
						savingsUtils.buildMessage(byNameAndBranch, 0, System.currentTimeMillis()),
						paginationSavingsButton.keyboardMarkup(byNameAndBranch, userBranch, 0, name),
						client);
				})))
			.switchIfEmpty(
				savingsService.listAllBranch(name)
					.flatMap(branches -> Mono.fromRunnable(() -> {
						if (branches == null || branches.isEmpty()) {
							sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
							return;
						}
						sendMessage(chatId, "Data ditemukan dalam beberapa cabang, pilih cabang:", selectSavingsBranch.dynamicSelectBranch(branches, name), client);
					}))
			)
			.onErrorResume(e -> {
				log.error("Error /tab chatId={} text='{}': {}", chatId, text, e.getMessage(), e);
				return Mono.fromRunnable(() -> sendMessage(chatId, "❌ Terjadi kesalahan, coba lagi", client));
			})
			.then();
	}
}
