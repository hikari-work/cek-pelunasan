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
import org.cekpelunasan.core.service.slik.SlikNameFormatter;
import org.cekpelunasan.core.service.slik.SlikSessionCache;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.button.SlikButtonConfirmation;
import org.cekpelunasan.utils.button.SlikNamePaginationButton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlikCommand extends AbstractCommandHandler {

	private static final String KTP_ID_PATTERN  = "\\b\\d{16}\\b";
	private static final String COMMAND_PREFIX  = "/slik ";
	private static final String KTP_PREFIX      = "KTP_";
	private static final String KTP_EXTENSION   = ".txt";

	private static final String ERROR_KTP_REQUIRED       = "⚠️ No KTP harus diisi\n\nGunakan: `/slik <16 digit KTP ID>` atau `/slik <nama>`";
	private static final String ERROR_SLIK_NOT_REQUESTED = "❌ SLIK belum di-request atau Anda belum validasi AO\n\nCaranya: `/otor <3 Huruf Inisial>`";
	private static final String ERROR_PROCESSING         = "⚠️ Terjadi kesalahan saat memproses pencarian. Silakan coba lagi.";

	@Value("${slik.search-timeout-seconds:30}")
	private long searchTimeoutSeconds;

	@Value("${slik.max-results:50}")
	private int maxResults;

	private final S3ClientConfiguration s3Connector;
	private final UserService userService;
	private final PDFReader pdfReader;
	private final SlikButtonConfirmation slikButtonConfirmation;
	private final SlikNameFormatter slikNameFormatter;
	private final SlikSessionCache slikSessionCache;
	private final SlikNamePaginationButton paginationButton;

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
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String query = extractQuery(text);
		if (query.isEmpty()) {
			return Mono.fromRunnable(() -> telegramMessageService.sendText(chatId, ERROR_KTP_REQUIRED, client));
		}
		if (isValidKtpId(query)) {
			return Mono.fromRunnable(() -> handleKtpIdSearch(query, chatId, client));
		}
		return handleNameSearch(query, chatId, client);
	}

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

	private Mono<Void> handleNameSearch(String query, long chatId, SimpleTelegramClient client) {
		log.info("Processing name search — query: {}, chatId: {}", query, chatId);

		return userService.findUserByChatId(chatId)
			.switchIfEmpty(Mono.fromRunnable(() -> {
				log.warn("User not found for chatId: {}", chatId);
				telegramMessageService.sendText(chatId, ERROR_SLIK_NOT_REQUESTED, client);
			}))
			.flatMap(user -> buildSearchFlux(user, query, chatId, client))
			.onErrorResume(e -> {
				log.error("Error in name search — query: {}, chatId: {}", query, chatId, e);
				telegramMessageService.sendText(chatId, ERROR_PROCESSING, client);
				return Mono.<Void>empty();
			})
			.then();
	}

	private Mono<Void> buildSearchFlux(User user, String query, long chatId, SimpleTelegramClient client) {
		boolean isAdmin = AccountOfficerRoles.ADMIN == user.getRoles();

		if (!isAdmin && (user.getUserCode() == null || user.getUserCode().isBlank())) {
			telegramMessageService.sendText(chatId, ERROR_SLIK_NOT_REQUESTED, client);
			return Mono.empty();
		}

		String prefix     = isAdmin ? "" : user.getUserCode() + "_";
		String queryLower = query.trim().toLowerCase();

		return s3Connector.listObjectFoundByName(prefix)
			.filter(key -> !key.startsWith("KTP_") && key.toLowerCase().contains(queryLower))
			.take(maxResults)
			.flatMap(this::extractPageData, 10)
			.filter(p -> p.idNumber() != null || p.dto() != null)
			.collectList()
			.flatMap(pages -> {
				if (pages.isEmpty()) {
					telegramMessageService.sendText(chatId, buildNoResultsMessage(query), client);
					return Mono.empty();
				}
				slikSessionCache.put(chatId, new SlikSessionCache.SlikSession(pages, query));
				TdApi.FormattedText message  = slikNameFormatter.format(pages.getFirst(), 0, pages.size());
				TdApi.ReplyMarkupInlineKeyboard keyboard = paginationButton.build(0, pages.size());
				telegramMessageService.sendKeyboardFormatted(chatId, keyboard, client, message);
				log.info("Name search done — query: {}, total: {}, admin: {}", query, pages.size(), isAdmin);
				return Mono.<Void>empty();
			});
	}

	private Mono<SlikSessionCache.SlikPageData> extractPageData(String contentKey) {
		return s3Connector.getFile(contentKey)
			.flatMap(pdfBytes -> pdfReader.generateIDNumber(pdfBytes))
			.doOnNext(idNumber -> log.debug("Extracted idNumber={} from {}", idNumber, contentKey))
			.flatMap(idNumber -> {
				String ktpKey = KTP_PREFIX + idNumber + KTP_EXTENSION;
				return s3Connector.getFile(ktpKey)
					.doOnNext(b -> log.debug("Fetched {} ({} bytes)", ktpKey, b.length))
					.doOnError(e -> log.warn("Failed to fetch {} from S3: {}", ktpKey, e.getMessage()))
					.flatMap(jsonBytes -> slikNameFormatter.parse(jsonBytes)
						.map(dto -> new SlikSessionCache.SlikPageData(contentKey, idNumber, dto))
						.defaultIfEmpty(new SlikSessionCache.SlikPageData(contentKey, idNumber, null))
					)
					.defaultIfEmpty(new SlikSessionCache.SlikPageData(contentKey, idNumber, null));
			})
			.defaultIfEmpty(new SlikSessionCache.SlikPageData(contentKey, null, null))
			.timeout(java.time.Duration.ofSeconds(searchTimeoutSeconds))
			.onErrorResume(e -> {
				log.error("extractPageData failed for {}: {}", contentKey, e.getMessage());
				return Mono.just(new SlikSessionCache.SlikPageData(contentKey, null, null));
			});
	}

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

	private String extractQuery(String text) {
		return text.replace(COMMAND_PREFIX, "").trim();
	}

	private boolean isValidKtpId(String text) {
		return text.matches(KTP_ID_PATTERN);
	}
}
