package org.cekpelunasan.platform.telegram.command.handler;

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
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;
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
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String[] parts = text.split(" ");
			if (parts.length < 2) {
				sendMessage(chatId, "Gunakan /otor <kode cabang> atau\n/otor <kode ao>", telegramClient);
				return;
			}

			Optional<User> userOptional = userService.findUserByChatId(chatId);
			if (userOptional.isEmpty()) {
				sendMessage(chatId, "User tidak ditemukan", telegramClient);
				return;
			}

			User user = userOptional.get();
			String target = parts[1];

			if (target.length() == 3 && billService.findAllAccountOfficer().contains(target)) {
				registerUser(user, AccountOfficerRoles.AO, target, "AO", chatId, telegramClient);
				CompletableFuture.runAsync(sendNotificationSlikUpdated::runTest);
				return;
			}
			if (isNumber(target) && billService.lisAllBranch().contains(target)) {
				registerUser(user, AccountOfficerRoles.PIMP, target, "Pimpinan", chatId, telegramClient);
				CompletableFuture.runAsync(sendNotificationSlikUpdated::runTest);
				return;
			}
			sendMessage(chatId, "❌ *Format tidak valid*\n\nContoh: /otor 1234567890", telegramClient);
		});
	}

	private void registerUser(User user, AccountOfficerRoles role, String code, String label, long chatId, TelegramClient telegramClient) {
		user.setUserCode(code);
		user.setRoles(role);
		userRepository.save(user);
		sendMessage(chatId, "✅ User berhasil didaftarkan sebagai *" + label + "*", telegramClient);
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
