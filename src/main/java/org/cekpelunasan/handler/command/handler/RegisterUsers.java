package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.repository.UserRepository;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.slik.SendNotificationSlikUpdated;
import org.cekpelunasan.service.users.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class RegisterUsers implements CommandProcessor {
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
		return """
			Gunakan Command Ini untuk mendaftarkan user
			Berdasarkan User ID, Pimpinan atau AO
			""";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.AO, AccountOfficerRoles.PIMP, AccountOfficerRoles.ADMIN})
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CommandProcessor.super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String[] parts = text.split(" ");

			if (parts.length < 2) {
				sendMessage(chatId, getHelp(), telegramClient);
				return;
			}

			String target = parts[1];
			Optional<User> userOptional = userService.findUserByChatId(chatId);

			if (userOptional.isEmpty()) {
				sendMessage(chatId, "User tidak ditemukan", telegramClient);
				return;
			}

			User user = userOptional.get();
			if (target.length() == 3 && isValidAO(target)) {
                registerUser(user, AccountOfficerRoles.AO, target, "AO", chatId, telegramClient);
                CompletableFuture.runAsync(sendNotificationSlikUpdated::runTest);
                return;
            }
			if (isNumber(target) && isValidBranch(target)) {
                registerUser(user, AccountOfficerRoles.PIMP, target, "Pimpinan", chatId, telegramClient);
                CompletableFuture.runAsync(sendNotificationSlikUpdated::runTest);
                return;
            }
			sendMessage(chatId, "❌ *Format tidak valid*\n\nContoh: /otor 1234567890", telegramClient);
		});
	}

	private void registerUser(User user, AccountOfficerRoles role, String code, String label, Long chatId, TelegramClient telegramClient) {
		user.setUserCode(code);
		user.setRoles(role);
		userRepository.save(user);
		sendMessage(chatId, "✅ User berhasil didaftarkan sebagai *" + label + "*", telegramClient);
	}

	public String getHelp() {
		return """
			Gunakan /otor <kode cabang> atau
			/otor <kode ao>
			""";
	}

	public boolean isNumber(String str) {
		try {
			Long.parseLong(str);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public boolean isValidBranch(String branchCode) {
		return billService.lisAllBranch().contains(branchCode);
	}

	public boolean isValidAO(String aoCode) {
		return billService.findAllAccountOfficer().contains(aoCode);
	}
}

