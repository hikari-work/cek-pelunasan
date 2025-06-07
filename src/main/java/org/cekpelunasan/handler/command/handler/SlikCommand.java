package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.slik.GeneratePDF;
import org.cekpelunasan.service.slik.PDFReader;
import org.cekpelunasan.service.slik.S3Connector;
import org.cekpelunasan.service.users.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Handles SLIK commands for retrieving and processing KTP data.
 * Provides functionality to search by KTP ID or by name.
 */
@Component
public class SlikCommand implements CommandProcessor {
    private static final String KTP_PREFIX = "KTP_";
    private static final String KTP_EXTENSION = ".txt";
    private static final String KTP_ID_PATTERN = "\\b\\d{16}\\b";
    private static final String MARKDOWN_PARSE_MODE = "Markdown";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String COMMAND_PREFIX = "/slik ";
    private static final String LOADING_MESSAGE = "Mengambil Data KTP";
    private static final String FILE_NOT_FOUND_FORMAT = "Data KTP `%s` tidak ada";
    private static final String FILE_FOUND_FORMAT = "Data KTP `%s` Ditemukan....";
    private static final String ERROR_MESSAGE = "SLIK Belum di request, Atau anda belum didaftarkan sebagai AO";
    private static final String KTP_REQUIRED_MESSAGE = "No KTP Harus Diisi";

    private final S3Connector s3Connector;
    private final GeneratePDF generatePDF;
    private final AuthorizedChats authorizedChats;
    private final MessageTemplate messageTemplate;
    private final UserService userService;
    private final PDFReader pdfReader;

    public SlikCommand(
            S3Connector s3Connector,
            GeneratePDF generatePDF,
            AuthorizedChats authorizedChats,
            MessageTemplate messageTemplate,
            UserService userService,
            PDFReader pdfReader
    ) {
        this.s3Connector = s3Connector;
        this.generatePDF = generatePDF;
        this.authorizedChats = authorizedChats;
        this.messageTemplate = messageTemplate;
        this.userService = userService;
        this.pdfReader = pdfReader;
    }

    @Override
    public String getCommand() {
        return "/slik";
    }

    @Override
    public String getDescription() {
        return "Retrieve KTP data by ID or search by name";
    }

    @Override
    @Async
    public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            if (!authorizedChats.isAuthorized(chatId)) {
                sendMessage(chatId, messageTemplate.unathorizedMessage(), telegramClient);
                return;
            }
            
            String query = extractQuery(text);
            if (query.isEmpty()) {
                sendMessage(chatId, KTP_REQUIRED_MESSAGE, telegramClient);
                return;
            }
            
            if (isValidKtpId(query)) {
                processSlikSearchById(query, chatId, telegramClient);
            } else {
                processSlikSearchByName(query, chatId, telegramClient);
            }
        });
    }
    
    /**
     * Extracts the search query from the command text.
     */
    private String extractQuery(String text) {
        return text.replace(COMMAND_PREFIX, "").trim();
    }
    
    /**
     * Validates if the given text is a valid KTP ID (16 digits).
     */
    private boolean isValidKtpId(String text) {
        return text.matches(KTP_ID_PATTERN);
    }
    
    /**
     * Processes a search by name query.
     */
    private void processSlikSearchByName(String query, long chatId, TelegramClient telegramClient) {
        String result = handleSlikByNameCommand(query, chatId);
        if (result == null) {
            sendMessage(chatId, ERROR_MESSAGE, telegramClient);
            return;
        }
        sendMessage(chatId, result, telegramClient);
    }
    
    /**
     * Processes a search by KTP ID query.
     */
    private void processSlikSearchById(String ktpId, long chatId, TelegramClient telegramClient) {
        Message notification = sendNotification(chatId, telegramClient);
        if (notification == null) {
            return;
        }
        
        String filename = KTP_PREFIX + ktpId + KTP_EXTENSION;
        byte[] files = s3Connector.getFile(filename);
        
        if (files == null) {
            log.info("File not found for KTP ID: {}", ktpId);
            editMessage(chatId, notification.getMessageId(), 
                    String.format(FILE_NOT_FOUND_FORMAT, ktpId), telegramClient);
            return;
        }
        
        processKtpFile(ktpId, files, chatId, notification.getMessageId(), telegramClient);
    }

    /**
     * Processes a KTP file to generate and send a PDF.
     */
    private void processKtpFile(String ktpId, byte[] files, long chatId, int messageId, TelegramClient telegramClient) {
        String htmlContent = generatePDF.sendBytesWithRestTemplate(files, ktpId + KTP_EXTENSION);
        if (htmlContent == null || htmlContent.isEmpty()) {
            log.info("Failed to generate HTML content for KTP ID: {}", ktpId);
            editMessage(chatId, messageId, String.format(FILE_NOT_FOUND_FORMAT, ktpId), telegramClient);
            return;
        }
        
        byte[] pdfBytes = generatePDF.convertHtmlToPdf(htmlContent);
        editMessage(chatId, messageId, String.format(FILE_FOUND_FORMAT, ktpId), telegramClient);
        
        if (pdfBytes == null || pdfBytes.length == 0) {
            log.info("Failed to generate PDF for KTP ID: {}", ktpId);
            sendMessage(chatId, "File not found: " + ktpId, telegramClient);
            return;
        }
        
        log.info("Sending PDF file for KTP ID: {}", ktpId);
        InputFile pdfFile = new InputFile(new ByteArrayInputStream(pdfBytes), ktpId + ".pdf");
        sendDocument(chatId, ktpId, pdfFile, telegramClient);
    }

    /**
     * Sends a notification message to indicate loading is in progress.
     */
    private Message sendNotification(Long chatId, TelegramClient telegramClient) {
        try {
            return telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(LOADING_MESSAGE)
                    .parseMode(MARKDOWN_PARSE_MODE)
                    .build());
        } catch (Exception e) {
            log.info("Error sending notification: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Updates an existing message with new text.
     */
    private void editMessage(Long chatId, Integer messageId, String text, TelegramClient telegramClient) {
        try {
            telegramClient.execute(EditMessageText.builder()
                    .chatId(chatId)
                    .text(text)
                    .messageId(messageId)
                    .parseMode(MARKDOWN_PARSE_MODE)
                    .build());
        } catch (Exception e) {
            log.info("Error editing message: {}", e.getMessage());
        }
    }
    
    /**
     * Handles the search by name command logic.
     */
    public String handleSlikByNameCommand(String text, long chatId) {
        log.info("Searching for: {}", text);
        Optional<User> userByChatId = userService.findUserByChatId(chatId);
        
        if (userByChatId.isEmpty()) {
            return null;
        }
        
        User user = userByChatId.get();
        String searchKey = user.getUserCode() + "_" + text.trim();
        log.info("Search key: {}", searchKey);
        List<String> results = s3Connector.listObjectFoundByName(searchKey);
        
        if (results.isEmpty()) {
            return buildNoResultsMessage(text);
        }
        
        return buildSearchResultsMessage(text, results, user);
    }
    
    /**
     * Builds a message for when no search results are found.
     */
    private String buildNoResultsMessage(String query) {
        return String.format("""
            🔍 *HASIL PENCARIAN*
            ━━━━━━━━━━━━━━━━━━━
            
            ❌ *Tidak ditemukan hasil untuk "%s"*
            Silahkan periksa lagi, apakah SLIK sudah di request atau belum?
            atau kamu belum validasi AO? Kalau belum silahkan validasi dahulu...
            Caranya :
            /otor <3 Huruf Inisial>
            
            _Silakan coba dengan kata kunci lain atau periksa penulisan karena pencarian case sensitive_
            """, query);
    }
    
    /**
     * Builds a message containing search results.
     */
    private String buildSearchResultsMessage(String query, List<String> documents, User user) {
        StringBuilder builder = new StringBuilder(String.format("""
            🔍 *HASIL PENCARIAN*
            ━━━━━━━━━━━━━━━━━━━
            
            📝 Kata kunci: *%s*
            🔢 Ditemukan: *%d dokumen*
            👤 User: *%s*
            
            📋 *DAFTAR DOKUMEN*
            """, query, documents.size(), user.getUserCode()));
        
        int counter = 1;
        for (String contentKey : documents) {
            log.info("Document: {}", contentKey);
            String idNumber = pdfReader.generateIDNumber(s3Connector.getFile(contentKey));
            builder.append(buildDocumentEntry(counter++, contentKey, idNumber));
        }
        
        builder.append("""
            ℹ️ _Tap pada Command untuk menyalin dan mendapatkan File_
            ⏱️ _Generated:""").append(LocalDateTime.now().format(DATE_FORMATTER)).append("_");
        
        return builder.toString();
    }
    
    /**
     * Builds a single document entry for the search results.
     */
    private String buildDocumentEntry(int index, String contentKey, String idNumber) {
        return String.format("""
            
            📄 *Dokumen #%d*
            ┌─────────────────────
            │ 📂 Nama: `%s`
            │ 🪪 No KTP: %s
            │ 🪪 Command Resume: %s
            │ 🪪 Original SLIK: %s
            └─────────────────────
            """, 
            index,
            contentKey,
            (idNumber != null ? "`" + idNumber + "`" : "_Tidak ditemukan_"),
            (idNumber != null ? "`/slik " + idNumber + "`" : "_Tidak ditemukan_"),
            (contentKey != null ? "`/doc " + contentKey + "`" : "_Tidak ditemukan_")
        );
    }
}