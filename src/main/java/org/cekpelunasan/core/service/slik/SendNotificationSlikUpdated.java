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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Memantau bucket S3/R2 secara berkala dan mengirimkan notifikasi Telegram
 * ke pengguna yang relevan setiap kali ada file SLIK PDF baru yang diunggah.
 *
 * <p>Cara kerjanya: setiap menit, class ini menghitung jumlah file di bucket.
 * Jika jumlahnya berubah dari pengecekan sebelumnya, artinya ada file baru,
 * dan notifikasi langsung dikirim ke pengguna yang kode penggunanya cocok
 * dengan prefix nama file tersebut.</p>
 *
 * <p>File yang namanya dimulai dengan "KTP_" dikecualikan dari pengecekan
 * karena bukan merupakan laporan SLIK, melainkan foto KTP pendukung.</p>
 */
@Slf4j
@Component
public class SendNotificationSlikUpdated {

	@Value("${r2.bucket}")
	private String bucket;
	private volatile int lastCount = 1;

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
	 * Tugas terjadwal yang berjalan setiap 60 detik. Menghitung total file
	 * di bucket S3/R2 (termasuk halaman yang dipaginasi), lalu membandingkannya
	 * dengan jumlah terakhir yang dicatat. Jika jumlahnya sama, tidak ada yang
	 * dilakukan. Jika berubah, notifikasi dikirim ke pengguna terkait.
	 */
	@Scheduled(fixedDelay = 60 * 1000L)
	public void runTest() {
		int currentFiles = 0;
		String continuationToken = null;
		do {
			ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
				.bucket(bucket);
			if (continuationToken != null) {
				requestBuilder.continuationToken(continuationToken);
			}
			try {
				ListObjectsV2Response response = CompletableFuture
					.supplyAsync(() -> s3AsyncClient.listObjectsV2(requestBuilder.build()))
					.get()
					.get();
				currentFiles += response.contents().size();
				continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
			} catch (Exception e) {
				log.error("Error listing S3 objects: {}", e.getMessage());
				return;
			}
		} while (continuationToken != null);

		if (currentFiles == lastCount) {
			log.info("PDF Count {}, Skipped", lastCount);
			return;
		}
		lastCount = currentFiles;
		log.info("PDF Count changed to {}, sending notifications", currentFiles);
		sendNotificationsForNewPdfs();
	}

	/**
	 * Mengambil daftar semua file PDF (bukan KTP) dari bucket, lalu
	 * mengelompokkannya berdasarkan prefix nama file (bagian sebelum underscore
	 * pertama). Untuk setiap prefix, dicari pengguna yang kode penggunanya
	 * cocok, lalu pesan notifikasi dikirim ke chat ID mereka.
	 *
	 * <p>Jika TDLight client belum siap (misalnya saat startup), proses ini
	 * dilewati dan dicatat ke log.</p>
	 */
	private void sendNotificationsForNewPdfs() {
		SimpleTelegramClient client = telegramBot.getClient();
		if (client == null) {
			log.warn("TDLight client not ready, skipping SLIK notification");
			return;
		}

		s3ClientConfiguration.listObjectFoundByName("")
			.filter(key -> key.endsWith(".pdf") && !key.startsWith("KTP_"))
			.collectList()
			.doOnNext(pdfList -> {
				if (pdfList.isEmpty()) {
					log.info("No PDF files found");
					return;
				}
				Map<String, List<String>> byPrefix = pdfList.stream()
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
							log.info("Sent SLIK notification to user {} (code={})", user.getChatId(), prefix);
						})
						.subscribe()
				);
			})
			.subscribe();
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
