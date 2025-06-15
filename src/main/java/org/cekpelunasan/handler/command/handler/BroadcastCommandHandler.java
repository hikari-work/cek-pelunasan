package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class BroadcastCommandHandler implements CommandProcessor {

	private static final Logger log = LoggerFactory.getLogger(BroadcastCommandHandler.class);
	private static final long DELAY_BETWEEN_USERS_MS = 500;

	private final UserService userService;
	private final String botOwner;
	private final MessageTemplate messageTemplate;

	public BroadcastCommandHandler(UserService userService,
								   @Value("${telegram.bot.owner}") String botOwner,
								   MessageTemplate messageTemplate) {
		this.userService = userService;
		this.botOwner = botOwner;
		this.messageTemplate = messageTemplate;
	}

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
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			Message message = update.getMessage();
			long chatId = message.getChatId();

			if (!botOwner.equalsIgnoreCase(String.valueOf(chatId))) {
				sendMessage(chatId, messageTemplate.notAdminUsers(), telegramClient);
				return;
			}

			// Harus balas pesan untuk di-copy
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
