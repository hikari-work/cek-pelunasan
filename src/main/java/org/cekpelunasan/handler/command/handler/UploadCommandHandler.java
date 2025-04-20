package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.RepaymentService;
import org.cekpelunasan.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class UploadCommandHandler implements CommandProcessor {

    private static final Logger log = LoggerFactory.getLogger(UploadCommandHandler.class);
    private static final long DELAY_BETWEEN_USERS_MS = 500;

    private final RepaymentService repaymentService;
    private final UserService userService;

    private final String botOwner;
    private final MessageTemplate messageTemplate;

    public UploadCommandHandler(RepaymentService repaymentService,
                                UserService userService,
                                @Value("${telegram.bot.owner}") String botOwner, MessageTemplate messageTemplate) {
        this.repaymentService = repaymentService;
        this.userService = userService;
        this.botOwner = botOwner;
        this.messageTemplate = messageTemplate;
    }

    @Override
    public String getCommand() {
        return "/upload";
    }

    @Override
    public String getDescription() {
        return """
                Upload data Pelunasan terbaru
                """;
    }

    @Override
    @Async
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();
            long start = System.currentTimeMillis();

            if (!botOwner.equalsIgnoreCase(String.valueOf(chatId))) {
                sendMessage(chatId, messageTemplate.notAdminUsers(), telegramClient);
                return;
            }

            try {
                String url = text.split(" ", 2)[1];
                String fileName = url.substring(url.lastIndexOf("/") + 1);

                sendMessage(chatId, "⏳ *Sedang mengunduh dan memproses file...*", telegramClient);

                List<User> allUsers = userService.findAllUsers();
                broadcastToUsers(allUsers,
                        "⚠ *Sedang melakukan update data, mohon jangan kirim perintah apapun...*",
                        telegramClient);

                if (downloadAndProcessFile(url, fileName)) {
                    String successMsg = String.format("✅ *File berhasil diproses:*\n\n_Eksekusi dalam %dms_",
                            System.currentTimeMillis() - start);
                    broadcastToUsers(allUsers, successMsg, telegramClient);
                } else {
                    broadcastToUsers(allUsers, "⚠ *Gagal update. Akan dicoba ulang.*", telegramClient);
                }

            } catch (ArrayIndexOutOfBoundsException e) {
                sendMessage(chatId, "❗ *Format salah.*\nGunakan `/upload <link_csv>`", telegramClient);
            }
        });
    }

    private void broadcastToUsers(List<User> users, String message, TelegramClient client) {
        for (User user : users) {
            sendMessage(user.getChatId(), message, client);
            try {
                Thread.sleep(DELAY_BETWEEN_USERS_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrupted saat delay antar user", e);
            }
        }
    }

    private boolean downloadAndProcessFile(String fileUrl, String fileName) {
        try (InputStream inputStream = new URL(fileUrl).openStream()) {
            Path output = Paths.get("files", fileName);
            Files.createDirectories(output.getParent());
            Files.copy(inputStream, output, StandardCopyOption.REPLACE_EXISTING);

            if (fileName.endsWith(".csv")) {
                repaymentService.parseCsvAndSaveIntoDatabase(output);
            }
            return true;
        } catch (Exception e) {
            log.error("❌ Gagal memproses file dari URL: {}", fileUrl, e);
            return false;
        }
    }
}
