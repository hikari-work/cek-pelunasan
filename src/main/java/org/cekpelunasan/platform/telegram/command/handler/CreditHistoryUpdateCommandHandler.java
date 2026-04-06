package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.credithistory.CreditHistoryService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.CsvDownloadUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

/**
 * Handler untuk perintah {@code /uploadcredit} — memperbarui data riwayat kredit dari file CSV.
 *
 * <p>Data riwayat kredit digunakan untuk fitur canvasing ({@code /canvasing}) yang menampilkan
 * mantan nasabah berdasarkan alamat. Untuk memperbarui datanya, admin cukup kirim perintah
 * diikuti URL file CSV: {@code /uploadcredit https://contoh.com/kredit.csv}.</p>
 *
 * <p>Saat proses update berjalan, semua user terdaftar akan mendapat notifikasi agar tidak
 * mengirim perintah apapun dulu. Setelah selesai, semua user juga mendapat konfirmasi bahwa
 * data sudah berhasil diperbarui.</p>
 *
 * <p>Hanya admin yang dapat menjalankan perintah ini.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditHistoryUpdateCommandHandler extends AbstractCommandHandler {

	private static final long DELAY_BETWEEN_USER = 500;

	private final UserService userService;
	private final CreditHistoryService creditHistoryService;

	/**
	 * Memvalidasi bahwa pengirim adalah admin sebelum memulai proses update.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return hasil proses upload, atau ditolak jika bukan admin
	 */
	@Override
	@RequireAuth(roles = AccountOfficerRoles.ADMIN)
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public String getCommand() {
		return "/uploadcredit";
	}

	@Override
	public String getDescription() {
		return "";
	}

	/**
	 * Mengunduh dan memproses file CSV riwayat kredit, lalu memberitahu semua user.
	 *
	 * <p>Alur kerjanya:</p>
	 * <ol>
	 *   <li>Ekstrak URL dari teks perintah. Jika tidak ada, kirim pesan format yang benar.</li>
	 *   <li>Ambil semua user terdaftar dan kirimkan notifikasi "sedang update" ke semua mereka.</li>
	 *   <li>Unduh file CSV dan proses datanya ke database melalui {@link CreditHistoryService}.</li>
	 *   <li>Kirim notifikasi hasil (berhasil atau gagal) ke semua user.</li>
	 * </ol>
	 *
	 * @param chatId ID chat admin yang mengirim perintah
	 * @param text   teks lengkap perintah yang berisi URL file CSV
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah seluruh proses update tuntas
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String fileUrl = CsvDownloadUtils.extractUrl(text);
		if (fileUrl == null) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "❗ *Format salah.*\nGunakan `/uploadcredit <link_csv>`", client));
		}
		return userService.findAllUsers()
			.collectList()
			.flatMap(allUsers -> {
				notifyUsers(allUsers, "⚠ *Sedang melakukan update data, mohon jangan kirim perintah apapun...*", client);
				if (!allUsers.isEmpty()) {
					sendMessage(allUsers.getFirst().getChatId(), "⏳ *Sedang update database canvasing*", client);
				}
				return Mono.fromCallable(() -> CsvDownloadUtils.downloadCsv(fileUrl))
					.flatMap(filePath -> creditHistoryService.parseCsvAndSaveIt(filePath))
					.doOnSuccess(v -> notifyUsers(allUsers, "✅ *Database berhasil di proses*", client))
					.onErrorResume(e -> {
						log.error("Gagal memproses file dari URL: {}", fileUrl, e);
						notifyUsers(allUsers, "⚠ *Gagal update. Akan dicoba ulang.*", client);
						return Mono.empty();
					});
			})
			.then();
	}

	/**
	 * Mengirimkan pesan notifikasi ke semua user dalam daftar, satu per satu dengan jeda 500ms.
	 *
	 * <p>Jeda antar pengiriman diperlukan untuk menghindari terkena rate limit dari Telegram.
	 * Jika thread diinterupsi di tengah proses, flag interupsi dipulihkan kembali agar tidak hilang.</p>
	 *
	 * @param users   daftar user yang akan menerima notifikasi
	 * @param message teks notifikasi yang akan dikirim
	 * @param client  koneksi aktif ke Telegram
	 */
	private void notifyUsers(java.util.List<org.cekpelunasan.core.entity.User> users, String message, SimpleTelegramClient client) {
		users.forEach(user -> {
			sendMessage(user.getChatId(), message, client);
			try {
				Thread.sleep(DELAY_BETWEEN_USER);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("Thread interrupted saat delay antar user", e);
			}
		});
	}
}
