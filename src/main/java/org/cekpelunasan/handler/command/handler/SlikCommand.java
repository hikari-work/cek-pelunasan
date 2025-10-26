package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.slik.IsUserGetPermissionToViewResume;
import org.cekpelunasan.service.slik.PDFReader;
import org.cekpelunasan.service.slik.S3ClientConfiguration;
import org.cekpelunasan.service.users.UserService;
import org.cekpelunasan.utils.button.SlikButtonConfirmation;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;


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
@RequiredArgsConstructor
public class SlikCommand implements CommandProcessor {
    private static final String KTP_ID_PATTERN = "\\b\\d{16}\\b";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String COMMAND_PREFIX = "/slik ";
    private static final String ERROR_MESSAGE = "SLIK Belum di request, Atau anda belum didaftarkan sebagai AO";
    private static final String KTP_REQUIRED_MESSAGE = "No KTP Harus Diisi";

    private final S3ClientConfiguration s3Connector;
    private final AuthorizedChats authorizedChats;
    private final MessageTemplate messageTemplate;
    private final UserService userService;
    private final PDFReader pdfReader;
	private final IsUserGetPermissionToViewResume isUserGetPermissionToViewResume;
	private final SlikButtonConfirmation slikButtonConfirmation;


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
				if (!isUserGetPermissionToViewResume.isUserGetPermissionToViewResume(query)) {
					sendMessage(chatId, "Anda tidak memiliki izin untuk melihat resume KTP ini!", telegramClient);
					return;
				}
				sendMessage(chatId, "Data Ditemukan Pilih Option Dibawah", slikButtonConfirmation.sendSlikCommand(query), telegramClient);
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