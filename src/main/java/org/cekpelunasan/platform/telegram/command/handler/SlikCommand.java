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

/**
 * Handler untuk perintah {@code /slik} — mencari dan menampilkan data laporan SLIK nasabah.
 *
 * <p>Perintah ini mendukung dua mode pencarian:</p>
 * <ul>
 *   <li><strong>Cari berdasarkan Nomor KTP (16 digit)</strong>: bot langsung menampilkan
 *       tombol pilihan mode laporan SLIK untuk KTP tersebut.
 *       Contoh: {@code /slik 3201234567890001}</li>
 *   <li><strong>Cari berdasarkan nama</strong>: bot menelusuri semua file SLIK yang
 *       tersimpan di S3 dan memfilter berdasarkan nama yang mengandung kata kunci yang diberikan.
 *       Hasilnya ditampilkan dengan paginasi.
 *       Contoh: {@code /slik Budi Santoso}</li>
 * </ul>
 *
 * <p>Untuk pencarian berdasarkan nama, AO hanya bisa melihat file SLIK yang ada di folder mereka sendiri
 * (diawali dengan kode AO), sementara admin bisa melihat semua file. Hasil pencarian disimpan
 * sementara di {@link SlikSessionCache} untuk mendukung navigasi paginasi.</p>
 *
 * <p>Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
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

	/**
	 * Memvalidasi peran pengguna sebelum memproses pencarian SLIK.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return hasil pencarian SLIK, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = { AccountOfficerRoles.AO, AccountOfficerRoles.ADMIN, AccountOfficerRoles.PIMP })
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Menentukan mode pencarian (KTP atau nama) dan mendelegasikan ke method yang sesuai.
	 *
	 * <p>Jika query kosong, bot menampilkan pesan error dengan petunjuk format yang benar.
	 * Jika query terdiri dari 16 digit angka, pencarian dilakukan berdasarkan KTP.
	 * Selain itu, pencarian dilakukan berdasarkan nama.</p>
	 *
	 * @param chatId ID chat pengguna yang mengirim perintah
	 * @param text   teks lengkap perintah termasuk query pencarian
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah hasil pencarian ditampilkan
	 */
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

	/**
	 * Menangani pencarian berdasarkan nomor KTP dengan menampilkan tombol pilihan mode laporan.
	 *
	 * <p>Tombol yang ditampilkan memungkinkan user memilih antara beberapa mode laporan SLIK
	 * yang tersedia untuk nomor KTP tersebut.</p>
	 *
	 * @param ktpId  nomor KTP 16 digit yang akan dicari
	 * @param chatId ID chat tujuan pengiriman hasil
	 * @param client koneksi aktif ke Telegram
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
	 * Menangani pencarian berdasarkan nama dengan menelusuri file SLIK di S3.
	 *
	 * <p>Memuat informasi user terlebih dahulu untuk menentukan prefix folder S3 yang boleh diakses,
	 * lalu menjalankan pencarian dan menampilkan hasilnya dengan paginasi.</p>
	 *
	 * @param query  kata kunci nama yang akan dicari
	 * @param chatId ID chat tujuan pengiriman hasil
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah hasil pencarian ditampilkan
	 */
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

	/**
	 * Membangun dan menjalankan pencarian file SLIK di S3 berdasarkan konteks user dan query.
	 *
	 * <p>Admin bisa mengakses semua file di S3 tanpa filter prefix. AO hanya bisa mengakses
	 * file dengan prefix kode AO mereka. File yang namanya diawali {@code KTP_} dilewati
	 * karena itu adalah file metadata KTP, bukan file laporan SLIK. Hasil disimpan ke
	 * {@link SlikSessionCache} untuk mendukung navigasi paginasi selanjutnya.</p>
	 *
	 * @param user   data user yang melakukan pencarian (menentukan akses S3)
	 * @param query  kata kunci pencarian nama
	 * @param chatId ID chat tujuan pengiriman hasil
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah hasil pencarian disusun dan dikirim
	 */
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

	/**
	 * Mengekstrak data halaman SLIK dari sebuah file di S3, termasuk nomor KTP dan data identitas nasabah.
	 *
	 * <p>Proses ekstraksi dilakukan secara bertahap:</p>
	 * <ol>
	 *   <li>Ambil file PDF dari S3 berdasarkan kunci konten.</li>
	 *   <li>Baca nomor ID dari PDF menggunakan {@link PDFReader}.</li>
	 *   <li>Ambil file metadata KTP dari S3 menggunakan nomor ID tersebut.</li>
	 *   <li>Parse data dari file metadata dan kembalikan sebagai {@link SlikSessionCache.SlikPageData}.</li>
	 * </ol>
	 * <p>Setiap langkah memiliki fallback jika file tidak ditemukan atau gagal diproses.
	 * Timeout diterapkan per file sesuai konfigurasi {@code slik.search-timeout-seconds}.</p>
	 *
	 * @param contentKey kunci objek S3 dari file SLIK yang akan diekstrak datanya
	 * @return {@link Mono} berisi data halaman SLIK, dengan field {@code null} jika ekstraksi gagal
	 */
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

	/**
	 * Membangun pesan yang ditampilkan ketika pencarian nama tidak menghasilkan data apapun.
	 *
	 * <p>Pesan mencakup kemungkinan penyebab tidak ditemukannya data dan saran langkah selanjutnya.</p>
	 *
	 * @param query kata kunci pencarian yang tidak menghasilkan hasil
	 * @return string pesan "tidak ditemukan" yang informatif untuk dikirim ke user
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
	 * Mengekstrak query pencarian dari teks perintah dengan menghapus prefix {@code /slik }.
	 *
	 * @param text teks lengkap perintah yang dikirim user
	 * @return query pencarian yang sudah bersih dari prefix perintah
	 */
	private String extractQuery(String text) {
		return text.replace(COMMAND_PREFIX, "").trim();
	}

	/**
	 * Memeriksa apakah teks yang diberikan merupakan nomor KTP yang valid (16 digit angka).
	 *
	 * @param text teks yang akan diperiksa
	 * @return {@code true} jika teks cocok dengan pola 16 digit angka, {@code false} jika tidak
	 */
	private boolean isValidKtpId(String text) {
		return text.matches(KTP_ID_PATTERN);
	}
}
