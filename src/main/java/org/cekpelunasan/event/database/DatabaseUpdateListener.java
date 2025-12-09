package org.cekpelunasan.event.database;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.service.users.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Listener for database update events.
 * <p>
 * This class listens for {@link DatabaseUpdateEvent} and sends a notification
 * via Telegram
 * to all registered users when an event occurs.
 * </p>
 */
@Slf4j
@Component
public class DatabaseUpdateListener {

	/**
	 * Client for interacting with the Telegram Bot API.
	 */
	private final TelegramClient telegramClient;

	/**
	 * Service for user-related operations.
	 */
	private final UserService userService;

	/**
	 * Constructs a new DatabaseUpdateListener.
	 *
	 * @param botToken    the Telegram bot token
	 * @param userService the user service
	 */
	public DatabaseUpdateListener(@Value("${telegram.bot.token}") String botToken, UserService userService) {
		this.userService = userService;
		this.telegramClient = new OkHttpTelegramClient(botToken);
	}

	/**
	 * Handles the database update event.
	 * <p>
	 * This method is executed asynchronously. It builds a message from the event
	 * and sends it to all users via Telegram.
	 * </p>
	 *
	 * @param event the database update event
	 */
	@Async
	@EventListener(DatabaseUpdateEvent.class)
	public void onDatabaseUpdateEvent(DatabaseUpdateEvent event) {
		log.info("Processing database update event asynchronously: {}", event.getEventType());

		try {
			String message = buildEventMessage(event);
			List<User> users = getAllUsers();

			// Kirim pesan ke semua user secara asynchronous
			users.forEach(user -> sendTelegramMessageAsync(user.getChatId().toString(), message));

			log.info("Database update event processing completed");
		} catch (Exception e) {
			log.error("Error processing database update event", e);
		}
	}

	/**
	 * Sends a Telegram message asynchronously.
	 *
	 * @param chatId      the chat ID to send the message to
	 * @param messageText the text of the message
	 */
	@Async
	public void sendTelegramMessageAsync(String chatId, String messageText) {
		CompletableFuture.runAsync(() -> {
			try {
				SendMessage message = SendMessage.builder()
						.chatId(chatId)
						.text(messageText)
						.build();

				telegramClient.execute(message);
				log.debug("Message sent successfully to chatId: {}", chatId);
			} catch (TelegramApiException e) {
				log.error("Failed to send telegram message to chatId: {}", chatId, e);
			} catch (Exception e) {
				log.error("Unexpected error while sending telegram message to chatId: {}", chatId, e);
			}
		});
	}

	/**
	 * Builds the event message string.
	 *
	 * @param event the database update event
	 * @return the formatted event message
	 */
	private String buildEventMessage(DatabaseUpdateEvent event) {
		String timestamp = java.time.LocalDateTime.now()
				.format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm:ss",
						Locale.forLanguageTag("id-ID")));

		String emoji = event.isSuccess() ? "✅" : "❌";
		String statusText = event.isSuccess() ? "berhasil" : "gagal";

		return String.format(
				"%s Database %s %s di update pada %s:",
				emoji,
				event.getEventType().value,
				statusText,
				timestamp);
	}

	/**
	 * Retrieves all users from the database.
	 *
	 * @return a list of all users
	 */
	private List<User> getAllUsers() {
		return userService.findAllUsers();
	}
}