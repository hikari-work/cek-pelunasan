package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.users.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class BillsUpdateDataHandler implements CommandProcessor {

	private static final long DELAY_BETWEEN_USER = 500;

	private final BillService billService;
	private final UserService userService;
	private final String botOwner;
	private final MessageTemplate messageTemplate;

	public BillsUpdateDataHandler(BillService billService,
								  @Value("${telegram.bot.owner}") String botOwner,
								  UserService userService, MessageTemplate messageTemplate1) {
		this.billService = billService;
		this.botOwner = botOwner;
		this.userService = userService;
		this.messageTemplate = messageTemplate1;
	}

	@Override
	public String getCommand() {
		return "/uploadtagihan";
	}

	@Override
	public String getDescription() {
		return """
			Gunakan Command ini untuk upload data tagihan harian.
			""";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			log.info("Upadte");
			String[] parts = text.split(" ", 2);

			if (!botOwner.equalsIgnoreCase(String.valueOf(chatId))) {
				sendMessage(chatId, messageTemplate.notAdminUsers(), telegramClient);
				return;
			}

			if (!String.valueOf(chatId).equalsIgnoreCase(botOwner) || parts.length < 2) {
				log.info("Denied");
				return;
			}
			log.info("Command: {}", text);
			String url = parts[1];
			String fileName = url.substring(url.lastIndexOf("/") + 1);

			sendMessage(chatId, "⏳ *Sedang mengunduh dan memproses file...*", telegramClient);

			try {
				List<User> users = userService.findAllUsers();

				if (processCsvFile(url, fileName)) {
					broadcast(users, String.format("✅ *Database tagihan berhasil di update pada %s:*",
						LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"))),
						telegramClient);
				} else {
					broadcast(users, "⚠ *Gagal update data Tagihan. Akan dicoba ulang.*", telegramClient);
				}
			} catch (Exception e) {
				log.error("❌ Gagal memproses file CSV", e);
				sendMessage(chatId, "❌ Gagal memproses file", telegramClient);
			}
		});
	}

	private boolean processCsvFile(String fileUrl, String fileName) {
		if (!fileName.endsWith(".csv")) return false;
		try (InputStream input = new URL(fileUrl).openStream()) {
			Path filePath = Paths.get("files", fileName);
			Files.createDirectories(filePath.getParent());
			Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
			billService.parseCsvAndSaveIntoDatabase(filePath);
			return true;
		} catch (Exception e) {
			log.error("❌ Gagal memproses file dari URL: {}", fileUrl, e);
			return false;
		}
	}

	private void broadcast(List<User> users, String message, TelegramClient client) {
		users.forEach(user -> {
			sendMessage(user.getChatId(), message, client);
			try {
				Thread.sleep(DELAY_BETWEEN_USER);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("❗ Delay antar user terinterupsi", e);
			}
		});
	}
}
