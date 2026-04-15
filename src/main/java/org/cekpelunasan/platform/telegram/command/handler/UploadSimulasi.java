package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.simulasi.SimulasiService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.platform.telegram.service.UploadProgressService;
import org.cekpelunasan.utils.CsvDownloadUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Handler untuk perintah {@code /uploadsimulasi} — memperbarui data simulasi pembayaran dari file CSV.
 *
 * <p>Data simulasi digunakan oleh fitur {@code /simulasi} dan {@code /minimal} untuk menghitung
 * rincian pembayaran angsuran kredit. Untuk memperbarui datanya, admin cukup kirim perintah
 * diikuti URL file CSV: {@code /uploadsimulasi https://contoh.com/simulasi.csv}.</p>
 *
 * <p>Setelah proses selesai, semua user terdaftar mendapat notifikasi berisi tanggal dan waktu
 * update berhasil dilakukan (dalam zona waktu WIB, UTC+7). Jika gagal, semua user juga
 * mendapat pemberitahuan bahwa update tidak berhasil.</p>
 *
 * <p>Jeda 100ms di antara notifikasi ke setiap user diterapkan untuk menghindari rate limit Telegram.
 * Hanya admin yang dapat menjalankan perintah ini.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadSimulasi extends AbstractCommandHandler {

	private static final long DELAY_BETWEEN_USERS_MS = 100;

	private final UserService userService;
	private final SimulasiService simulasiService;
	private final UploadProgressService progressService;

	@Override
	public String getCommand() {
		return "/uploadsimulasi";
	}

	@Override
	public String getDescription() {
		return "";
	}

	/**
	 * Memvalidasi bahwa pengirim adalah admin sebelum memulai proses upload data simulasi.
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

	/**
	 * Mengunduh file CSV simulasi dari URL yang disertakan, memprosesnya, lalu memberitahu semua user.
	 *
	 * <p>Alur kerjanya:</p>
	 * <ol>
	 *   <li>Ekstrak URL dari teks perintah. Jika tidak ada, minta admin mengisi URL.</li>
	 *   <li>Catat waktu saat ini (WIB) untuk digunakan di pesan notifikasi.</li>
	 *   <li>Ambil semua user terdaftar.</li>
	 *   <li>Unduh file CSV dan proses datanya melalui {@link SimulasiService}.</li>
	 *   <li>Kirim notifikasi hasil (berhasil atau gagal) ke semua user dengan jeda 100ms per user.</li>
	 * </ol>
	 *
	 * @param chatId ID chat admin yang mengirim perintah
	 * @param text   teks lengkap perintah yang berisi URL file CSV
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah seluruh proses selesai dan notifikasi terkirim
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String fileUrl = CsvDownloadUtils.extractUrl(text);
		if (fileUrl == null) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "Url Nya Diisi Bang", client));
		}
		String currentDateTime = LocalDateTime.now(ZoneOffset.ofHours(7))
			.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));
		return userService.findAllUsers()
			.collectList()
			.flatMap(users -> Mono.fromCallable(() -> CsvDownloadUtils.downloadCsv(fileUrl))
				.flatMap(filePath -> {
					long total = progressService.countLines(filePath);
					long[] msgIdRef = {progressService.sendProgressMessage(chatId, "Data Simulasi", total, client)};
					return simulasiService.parseCsv(filePath, total,
						done -> progressService.updateProgress(chatId, msgIdRef[0], "Data Simulasi", done, total, client));
				})
				.doOnSuccess(v -> notifyUsers(users, String.format("✅ *Update berhasil: Data Simulasi diperbarui pada %s*", currentDateTime), client))
				.onErrorResume(e -> {
					log.error("Gagal memproses file dari URL: {}", fileUrl, e);
					notifyUsers(users, "⚠ *Gagal update. Data Pelunasan, Akan dicoba ulang.*", client);
					return Mono.empty();
				}))
			.then();
	}

	/**
	 * Mengirimkan pesan notifikasi ke semua user dalam daftar, satu per satu dengan jeda 100ms.
	 *
	 * <p>Jeda antar pengiriman diterapkan untuk menghindari terkena rate limit dari Telegram.
	 * Jika thread diinterupsi di tengah proses, flag interupsi dipulihkan kembali agar tidak hilang.</p>
	 *
	 * @param users   daftar user yang akan menerima notifikasi
	 * @param message teks notifikasi yang akan dikirim ke setiap user
	 * @param client  koneksi aktif ke Telegram
	 */
	private void notifyUsers(java.util.List<org.cekpelunasan.core.entity.User> users, String message, SimpleTelegramClient client) {
		users.forEach(user -> {
			sendMessage(user.getChatId(), message, client);
			try {
				Thread.sleep(DELAY_BETWEEN_USERS_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("Thread interrupted saat delay antar user", e);
			}
		});
	}
}
