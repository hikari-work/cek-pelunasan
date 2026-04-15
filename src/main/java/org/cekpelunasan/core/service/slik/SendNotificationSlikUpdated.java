package org.cekpelunasan.core.service.slik;

import it.tdlight.client.SimpleTelegramClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.platform.telegram.bot.TelegramBot;
import org.cekpelunasan.platform.telegram.service.TelegramMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Memantau bucket S3/R2 secara berkala dan mengirimkan notifikasi Telegram
 * ke pengguna yang relevan setiap kali ada file SLIK PDF baru yang diunggah.
 *
 * <p>State notifikasi disimpan secara persisten sebagai tag S3 ({@code notified=true})
 * pada masing-masing objek di bucket. Dengan cara ini, meski aplikasi di-restart,
 * file yang sudah pernah dinotifikasi tidak akan dikirimkan ulang.</p>
 *
 * <p>File yang namanya dimulai dengan "KTP_" dikecualikan dari pengecekan
 * karena bukan merupakan laporan SLIK, melainkan foto KTP pendukung.</p>
 */
@Slf4j
@Component
public class SendNotificationSlikUpdated {

	@Value("${r2.bucket}")
	private String bucket;

	private final S3AsyncClient s3AsyncClient;
	private final S3ClientConfiguration s3ClientConfiguration;
	private final UserService userService;
	private final TelegramMessageService telegramMessageService;
	private final TelegramBot telegramBot;

	public SendNotificationSlikUpdated(
			S3AsyncClient s3AsyncClient,
			S3ClientConfiguration s3ClientConfiguration,
			UserService userService,
			TelegramMessageService telegramMessageService,
			@Lazy TelegramBot telegramBot) {
		this.s3AsyncClient = s3AsyncClient;
		this.s3ClientConfiguration = s3ClientConfiguration;
		this.userService = userService;
		this.telegramMessageService = telegramMessageService;
		this.telegramBot = telegramBot;
	}

	/**
	 * Tugas terjadwal yang berjalan setiap 60 detik. Mengambil semua file PDF
	 * di bucket (kecuali KTP_), lalu menyaring hanya yang belum memiliki tag
	 * {@code notified=true}. Jika ada file baru, notifikasi dikirim ke pengguna
	 * yang kode penggunanya cocok dengan prefix nama file, kemudian file tersebut
	 * di-tag agar tidak dikirimkan ulang pada pengecekan berikutnya.
	 */
	@Scheduled(fixedDelay = 60 * 1000L)
	public void runTest() {
		SimpleTelegramClient client = telegramBot.getClient();
		if (client == null) {
			log.warn("TDLight client not ready, skipping SLIK notification");
			return;
		}

		s3ClientConfiguration.listObjectFoundByName("")
			.filter(key -> key.endsWith(".pdf") && !key.startsWith("KTP_"))
			.filterWhen(key -> s3ClientConfiguration.isAlreadyNotified(key).map(notified -> !notified))
			.collectList()
			.doOnNext(newFiles -> {
				if (newFiles.isEmpty()) {
					log.info("Tidak ada file SLIK baru, semua sudah dinotifikasi");
					return;
				}
				log.info("Ditemukan {} file SLIK baru, mengirim notifikasi...", newFiles.size());
				sendNotifications(newFiles, client);
				tagAsNotified(newFiles);
			})
			.subscribe();
	}

	/**
	 * Mengelompokkan file berdasarkan prefix (kode AO) lalu mengirimkan notifikasi
	 * ke pengguna yang kode penggunanya cocok.
	 *
	 * @param newFiles daftar key file PDF yang belum pernah dinotifikasi
	 * @param client   TDLight client yang aktif
	 */
	private void sendNotifications(List<String> newFiles, SimpleTelegramClient client) {
		Map<String, List<String>> byPrefix = newFiles.stream()
			.collect(Collectors.groupingBy(key -> {
				int idx = key.indexOf('_');
				return idx > 0 ? key.substring(0, idx) : key;
			}));

		byPrefix.forEach((prefix, files) ->
			userService.findAllUsers()
				.filter(user -> prefix.equalsIgnoreCase(user.getUserCode()))
				.doOnNext(user -> {
					String message = buildNotificationMessage(prefix, files);
					telegramMessageService.sendText(user.getChatId(), message, client);
					log.info("Notifikasi SLIK terkirim ke user {} (kode={})", user.getChatId(), prefix);
				})
				.subscribe()
		);
	}

	/**
	 * Menandai setiap file dalam daftar dengan tag {@code notified=true} di S3/R2
	 * agar tidak diproses ulang pada siklus berikutnya maupun setelah restart.
	 *
	 * @param keys daftar key file yang baru saja dinotifikasi
	 */
	private void tagAsNotified(List<String> keys) {
		keys.forEach(key ->
			s3ClientConfiguration.tagObjectAsNotified(key)
				.doOnSuccess(v -> log.debug("Tagged as notified: {}", key))
				.subscribe()
		);
	}

	/**
	 * Membangun teks pesan notifikasi yang akan dikirim ke pengguna.
	 * Pesan berisi kode pengguna, jumlah file, dan daftar lengkap nama file
	 * yang tersedia di bucket.
	 *
	 * @param prefix kode pengguna (prefix nama file) yang menjadi pemilik laporan
	 * @param files  daftar nama file PDF yang terkait dengan prefix tersebut
	 * @return string pesan notifikasi dalam format Markdown Telegram
	 */
	private String buildNotificationMessage(String prefix, List<String> files) {
		StringBuilder sb = new StringBuilder();
		sb.append("📄 *SLIK Update*\n");
		sb.append(String.format("Kode: *%s*\n", prefix));
		sb.append(String.format("Total file: *%d*\n\n", files.size()));
		sb.append("*Daftar File:*\n");
		files.forEach(file -> sb.append("• `").append(file).append("`\n"));
		return sb.toString();
	}
}
