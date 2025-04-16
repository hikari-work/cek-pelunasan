package org.cekpelunasan.bot;

import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.service.RepaymentService;
import org.cekpelunasan.utils.PenaltyUtils;
import org.cekpelunasan.utils.RepaymentCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.util.Map;

@Component
public class TelegramMainBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramMainBot.class);

    private final TelegramClient telegramClient;
    private final String botToken;
    private final String ownerId;
    private final RepaymentService repaymentService;

    public TelegramMainBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.owner}") String ownerId,
            RepaymentService repaymentService
    ) {
        this.botToken = botToken;
        this.ownerId = ownerId;
        this.repaymentService = repaymentService;
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || update.getMessage().getText() == null) return;

        String chatId = update.getMessage().getChatId().toString();
        String text = update.getMessage().getText();


        if (text.startsWith("/upload ") && chatId.equals(ownerId)) {
            handleUploadCommand(chatId, text);
        } else if (text.equals("/help")) {
            sendMessage(chatId, "Silakan kirim perintah /upload <link_file_csv>");
        } else if (text.equals("/start")) {
            sendMessage(chatId, "Selamat datang di Pelunasan Bot!\nCari dengan menggunakan /pl kemudian No SPK");
        } else if (text.equals("/status")) {
            handleStatusCommand(chatId);
        } else if (text.startsWith("/pl ")) {
            handlePlCommand(chatId, text);
        } else {
            sendMessage(ownerId, "Pesan dari " + update.getMessage().getFrom().getUserName() + ": " + text);
        }
    }

    private void handleUploadCommand(String chatId, String text) {
        String link = text.split(" ", 2)[1];
        String fileName = link.substring(link.lastIndexOf("/") + 1);

        sendMessage(chatId, "Sedang mengunduh file: " + fileName);

        if (downloadAndProcessCsv(link, fileName)) {
            sendMessage(chatId, "✅ File berhasil diproses ke database: " + fileName);
        } else {
            sendMessage(chatId, "❌ Gagal memproses file: " + fileName);
        }
    }

    private void handleStatusCommand(String chatId) {
        Repayment latest = repaymentService.findAll();
        sendMessage(chatId, "Bot aktif dan siap menerima perintah.\nTerakhir update: " + latest.getCreatedAt());
    }

    private void handlePlCommand(String chatId, String text) {
        try {
            Long customerId = Long.parseLong(text.split(" ")[1]);
            Repayment repayment = repaymentService.findRepaymentById(customerId);

            if (repayment == null) {
                sendMessage(chatId, "Data tidak ditemukan untuk ID: " + customerId);
                return;
            }

            Map<String, Long> penalty = new PenaltyUtils().penalty(
                    repayment.getStartDate(),
                    repayment.getPenaltyLoan(),
                    repayment.getProduct()
            );

            String result = new RepaymentCalculator().calculate(repayment, penalty);
            sendMessage(chatId, result);

        } catch (NumberFormatException e) {
            sendMessage(chatId, "Format ID tidak valid.");
            log.warn("ID nasabah bukan angka: {}", text);
        } catch (Exception e) {
            sendMessage(chatId, "Terjadi kesalahan saat memproses data.");
            log.error("Kesalahan pada perintah /pl: ", e);
        }
    }

    private boolean downloadAndProcessCsv(String fileUrl, String fileName) {
        try (InputStream inputStream = new URL(fileUrl).openStream()) {
            Path output = Paths.get("files", fileName);
            Files.createDirectories(output.getParent());
            Files.copy(inputStream, output, StandardCopyOption.REPLACE_EXISTING);

            if (fileName.endsWith(".csv")) {
                repaymentService.parseCsvAndSaveIntoDatabase(output);
            }

            return true;
        } catch (Exception e) {
            log.warn("Gagal download/proses file: {}", e.getMessage());
            return false;
        }
    }

    private void sendMessage(String chatId, String message) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(message)
                    .parseMode("Markdown")
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Gagal kirim pesan: {}", e.getMessage());
        }
    }
}
