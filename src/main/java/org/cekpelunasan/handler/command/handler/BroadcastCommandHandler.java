package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.users.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor

public class BroadcastCommandHandler implements CommandProcessor {

	private static final long DELAY_BETWEEN_USERS_MS = 500;

	private final UserService userService;

	@Override
	public String getCommand() {
		return "/broadcast";
	}

	@Override
	public String getDescription() {
		return """
			Kirim pesan ke semua user terdaftar.
			Format: /broadcast <pesan>
			""";
	}

	@Override
	@Async
	@RequireAuth(roles = AccountOfficerRoles.ADMIN)
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			Message message = update.getMessage();
			long chatId = message.getChatId();

			if (message.getReplyToMessage() == null) {
				sendMessage(chatId, "❗ *Format salah.*\nBalas pesan yang mau di-broadcast, lalu ketik `/broadcast`", telegramClient);
				return;
			}

			Integer messageIdToCopy = message.getReplyToMessage().getMessageId();

			try {
				List<User> allUsers = userService.findAllUsers();

				for (User user : allUsers) {
					log.info("Copying To {}", user.getChatId());
					CopyMessage copyMessage = CopyMessage.builder()
						.fromChatId(String.valueOf(chatId))
						.messageId(messageIdToCopy)
						.chatId(String.valueOf(user.getChatId()))
						.build();

					telegramClient.execute(copyMessage);

					try {
						Thread.sleep(DELAY_BETWEEN_USERS_MS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						log.warn("Thread interrupted saat delay antar user", e);
					}
				}

				sendMessage(chatId, "✅ Broadcast copyMessage selesai ke " + allUsers.size() + " pengguna.", telegramClient);

			} catch (Exception e) {
				log.error("Gagal broadcast copyMessage", e);
				sendMessage(chatId, "❗ Gagal melakukan broadcast salinan pesan.", telegramClient);
			}
		});
	}

}
