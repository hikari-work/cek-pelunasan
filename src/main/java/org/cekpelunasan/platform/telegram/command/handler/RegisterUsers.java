package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.repository.UserRepository;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.slik.SendNotificationSlikUpdated;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class RegisterUsers extends AbstractCommandHandler {

	private final BillService billService;
	private final UserService userService;
	private final UserRepository userRepository;
	private final SendNotificationSlikUpdated sendNotificationSlikUpdated;

	@Override
	public String getCommand() {
		return "/otor";
	}

	@Override
	public String getDescription() {
		return "Gunakan Command Ini untuk mendaftarkan user Berdasarkan User ID, Pimpinan atau AO";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.AO, AccountOfficerRoles.PIMP, AccountOfficerRoles.ADMIN})
	public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String[] parts = text.split(" ");
		if (parts.length < 2) {
			sendMessage(chatId, "Gunakan /otor <kode cabang> atau\n/otor <kode ao>", client);
			return CompletableFuture.completedFuture(null);
		}

		String target = parts[1];

		Mono<Void> pipeline = userService.findUserByChatId(chatId)
			.switchIfEmpty(Mono.fromRunnable(() ->
				sendMessage(chatId, "User tidak ditemukan", client)))
			.flatMap(user -> {
				if (target.length() == 3) {
					return billService.findAllAccountOfficer()
						.flatMap(aoSet -> {
							if (aoSet.contains(target)) {
								return saveAndNotify(user, AccountOfficerRoles.AO, target, "AO", chatId, client);
							}
							return Mono.empty();
						});
				}
				if (isNumber(target)) {
					return billService.lisAllBranch()
						.flatMap(branchSet -> {
							if (branchSet.contains(target)) {
								return saveAndNotify(user, AccountOfficerRoles.PIMP, target, "Pimpinan", chatId, client);
							}
							return Mono.empty();
						});
				}
				sendMessage(chatId, "❌ *Format tidak valid*\n\nContoh: /otor 1234567890", client);
				return Mono.empty();
			})
			.then();

		return pipeline.toFuture();
	}

	private Mono<Void> saveAndNotify(User user, AccountOfficerRoles role, String code, String label, long chatId, SimpleTelegramClient client) {
		user.setUserCode(code);
		user.setRoles(role);
		return userRepository.save(user)
			.doOnSuccess(saved -> {
				sendMessage(chatId, "✅ User berhasil didaftarkan sebagai *" + label + "*", client);
				sendNotificationSlikUpdated.runTest();
			})
			.then();
	}

	private boolean isNumber(String str) {
		try {
			Long.parseLong(str);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
