package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.slik.PDFReader;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.button.SlikButtonConfirmation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;





import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Handles SLIK commands for retrieving and processing KTP data.
 * Provides functionality to search by KTP ID or by name with concurrent
 * processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlikCommand extends AbstractCommandHandler {

	private static final String KTP_ID_PATTERN = "\\b\\d{16}\\b";
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
	private static final String COMMAND_PREFIX = "/slik ";

	private static final String ERROR_KTP_REQUIRED = "⚠️ No KTP harus diisi\n\nGunakan: `/slik <16 digit KTP ID>` atau `/slik <nama>`";

	private static final String ERROR_SLIK_NOT_REQUESTED = "❌ SLIK belum di-request atau Anda belum validasi AO\n\nCaranya: `/otor <3 Huruf Inisial>`";
	private static final String ERROR_PROCESSING = "⚠️ Terjadi kesalahan saat memproses pencarian. Silakan coba lagi.";

	@Value("${slik.search-timeout-seconds:30}")
	private long searchTimeoutSeconds;

	@Value("${slik.max-results:50}")
	private int maxResults;

	private final S3ClientConfiguration s3Connector;
	private final UserService userService;
	private final PDFReader pdfReader;
	private final SlikButtonConfirmation slikButtonConfirmation;

	@Override
	public String getCommand() {
		return "/slik";
	}

	@Override
	public String getDescription() {
		return "Cari data KTP berdasarkan ID (16 digit) atau nama";
	}

	@Override
	@RequireAuth(roles = { AccountOfficerRoles.AO, AccountOfficerRoles.ADMIN, AccountOfficerRoles.PIMP })
	public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, SimpleTelegramClient client) {
		return CompletableFuture.runAsync(() -> {
			try {
				String query = extractQuery(text);
				if (query.isEmpty()) {
					telegramMessageService.sendText(chatId, ERROR_KTP_REQUIRED, client);
					return;
				}

				if (isValidKtpId(query)) {
					handleKtpIdSearch(query, chatId, client);
				} else {
					handleNameSearch(query, chatId, client);
				}

			} catch (Exception e) {
				log.error("Error processing SLIK command - Chat ID: {}", chatId, e);
				telegramMessageService.sendText(chatId, ERROR_PROCESSING, client);
			}
		});
	}

	/**
	 * Handles search by KTP ID (16 digits)
	 */
	private void handleKtpIdSearch(String ktpId, long chatId, SimpleTelegramClient client) {
		log.info("Processing KTP ID search - ID: {}", ktpId);

		try {
			TdApi.ReplyMarkupInlineKeyboard keyboard = slikButtonConfirmation.sendSlikCommand(ktpId);
			telegramMessageService.sendKeyboard(chatId, keyboard, client, "Silahkan Pilih untuk Mode Laporan Slik");

		} catch (Exception e) {
			log.error("Error in KTP ID search - ID: {}", ktpId, e);
			telegramMessageService.sendText(chatId, ERROR_PROCESSING, client);
		}
	}

	/**
	 * Handles search by name with concurrent ID extraction
	 */
	private void handleNameSearch(String query, long chatId, SimpleTelegramClient client) {
		log.info("Processing name search - Query: {}, Chat ID: {}", query, chatId);

		try {
			Optional<User> userOptional = userService.findUserByChatId(chatId);
			if (userOptional.isEmpty()) {
				log.warn("User not found for Chat ID: {}", chatId);
				telegramMessageService.sendText(chatId, ERROR_SLIK_NOT_REQUESTED, client);
				return;
			}

			User user = userOptional.get();
			String searchKey = user.getUserCode() + "_" + query.trim();

			log.debug("Searching S3 with key: {}", searchKey);
			List<String> results = s3Connector.listObjectFoundByName(searchKey);

			if (results.isEmpty()) {
				log.info("No results found for query: {}", query);
				telegramMessageService.sendText(chatId, buildNoResultsMessage(query), client);
				return;
			}

			if (results.size() > maxResults) {
				log.warn("Search returned too many results: {} (max: {})", results.size(), maxResults);
				results = results.stream().limit(maxResults).collect(Collectors.toList());
			}

			String searchResultsMessage = buildSearchResultsMessageConcurrent(query, results, user);
			telegramMessageService.sendText(chatId, searchResultsMessage, client);

		} catch (Exception e) {
			log.error("Error in name search - Query: {}, Chat ID: {}", query, chatId, e);
			telegramMessageService.sendText(chatId, ERROR_PROCESSING, client);
		}
	}

	/**
	 * Extracts the search query from command text
	 */
	private String extractQuery(String text) {
		return text.replace(COMMAND_PREFIX, "").trim();
	}

	/**
	 * Validates if text is a valid KTP ID (16 digits)
	 */
	private boolean isValidKtpId(String text) {
		return text.matches(KTP_ID_PATTERN);
	}

	/**
	 * Builds search results message with concurrent ID extraction
	 */
	private String buildSearchResultsMessageConcurrent(String query, List<String> documents, User user) {
		long startTime = System.currentTimeMillis();
		log.info("Building search results - Query: {}, Documents: {}", query, documents.size());

		StringBuilder builder = new StringBuilder(String.format("""
				🔍 *HASIL PENCARIAN*
				━━━━━━━━━━━━━━━━━━━

				📝 Kata kunci: *%s*
				🔢 Ditemukan: *%d dokumen*
				👤 User: *%s*

				📋 *DAFTAR DOKUMEN*
				""", query, documents.size(), user.getUserCode()));

		List<DocumentEntry> entries = extractDocumentEntriesConcurrent(documents);

		int counter = 1;
		for (DocumentEntry entry : entries) {
			builder.append(buildDocumentEntry(counter++, entry));
		}

		builder.append(String.format("""

				ℹ️ _Tap pada command untuk menyalin dan mendapatkan file_
				⏱️ _Generated: %s_""", LocalDateTime.now().format(DATE_FORMATTER)));

		long duration = System.currentTimeMillis() - startTime;
		log.info("Search results built in {} ms", duration);

		return builder.toString();
	}

	/**
	 * Extract document entries concurrently
	 */
	private List<DocumentEntry> extractDocumentEntriesConcurrent(List<String> documents) {
		List<CompletableFuture<DocumentEntry>> futures = documents.stream()
				.map(this::createExtractionTask)
				.toList();

		try {
			return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
					.thenApply(v -> futures.stream()
							.map(CompletableFuture::join)
							.collect(Collectors.toList()))
					.orTimeout(searchTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
					.get();
		} catch (InterruptedException | ExecutionException e) {
			log.error("Error in concurrent extraction", e);
			Thread.currentThread().interrupt();
			return documents.stream()
					.map(doc -> new DocumentEntry(doc, null))
					.collect(Collectors.toList());
		}
	}

	/**
	 * Create extraction task for a single document
	 */
	private CompletableFuture<DocumentEntry> createExtractionTask(String contentKey) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				log.debug("Extracting ID for document: {}", contentKey);
				byte[] fileContent = s3Connector.getFile(contentKey);

				if (fileContent == null || fileContent.length == 0) {
					log.warn("S3 file not found or empty: {}", contentKey);
					return new DocumentEntry(contentKey, null);
				}

				String idNumber = pdfReader.generateIDNumber(fileContent);
				log.debug("Extracted ID: {}, Document: {}", idNumber, contentKey);
				return new DocumentEntry(contentKey, idNumber);

			} catch (Exception e) {
				log.error("Error extracting document entry: {}", contentKey, e);
				return new DocumentEntry(contentKey, null);
			}
		})
				.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
				.exceptionally(ex -> {
					log.error("Timeout extracting document: {}", contentKey, ex);
					return new DocumentEntry(contentKey, null);
				});
	}

	/**
	 * Builds a message for no search results
	 */
	private String buildNoResultsMessage(String query) {
		return String.format("""
				🔍 *HASIL PENCARIAN*
				━━━━━━━━━━━━━━━━━━━

				❌ *Tidak ditemukan hasil untuk "%s"*

				Kemungkinan:
				• SLIK belum di-request
				• Anda belum validasi sebagai AO
				• Penulisan tidak sesuai (case sensitive)

				💡 Silakan validasi AO terlebih dahulu:
				`/otor <3 Huruf Inisial>`

				_Coba dengan kata kunci lain atau periksa penulisan kembali_
				""", query);
	}

	/**
	 * Builds a single document entry
	 */
	private String buildDocumentEntry(int index, DocumentEntry entry) {
		String idNumberDisplay = entry.idNumber() != null ? "`" + entry.idNumber() + "`" : "_Tidak ditemukan_";
		String ktpCommand = entry.idNumber() != null ? "`/slik " + entry.idNumber() + "`" : "_Tidak ditemukan_";

		return String.format("""

				📄 *Dokumen #%d*
				┌─────────────────────
				│ 📂 Nama: `%s`
				│ 🪪 No KTP: %s
				│ 🎯 Command: %s
				│ 📋 Original: `%s`
				└─────────────────────
				""",
				index,
				entry.contentKey(),
				idNumberDisplay,
				ktpCommand,
				"/doc " + entry.contentKey());
	}

	/**
	 * Inner class to hold document entry data
	 */
	private record DocumentEntry(String contentKey, String idNumber) {

	}
}