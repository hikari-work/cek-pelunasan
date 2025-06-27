package org.cekpelunasan.service.slik;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendNotificationSlikUpdated {

	@Value("${telegram.bot.token}")
	private String botToken;

	@Value("${r2.bucket}")
	private String bucket;
	private volatile int lastCount = 1;

	private final S3Connector s3Connector;
	private final UserRepository userRepository;


	private TelegramClient getClient() {
		return new OkHttpTelegramClient(botToken);
	}

	@Scheduled(fixedDelay = 10 * 60 * 1000)
	public void run() {
		List<S3Object> objects = new ArrayList<>();

		int currentPdf = 0;
		String continuationToken = null;
		do {
			ListObjectsV2Request request = ListObjectsV2Request.builder()
				.bucket(bucket)
				.continuationToken(continuationToken)
				.build();
			ListObjectsV2Response response = s3Connector.s3Client().listObjectsV2(request);

			for (S3Object object : response.contents()) {
				if (object.key().contains(".pdf")) {
					currentPdf++;
					objects.add(object);
				}
			}
			continuationToken = response.nextContinuationToken();
		} while (continuationToken != null);

		BlockingQueue<S3Object> queue = new LinkedBlockingQueue<>(objects);

		if (currentPdf == lastCount) {
			log.info("PDF Count {}, Skipped", lastCount);
			objects.clear();
			return;
		} else {
			lastCount = currentPdf;
		}
		checkNotification(queue);

	}

	private void checkNotification(BlockingQueue<S3Object> queue) {
		ExecutorService executorService = Executors.newFixedThreadPool(10);
		for (int i = 0; i < 10; i++) {
			executorService.submit(() -> {
				while (true) {
					try {
						S3Object object = queue.poll(2, TimeUnit.SECONDS);
						if (object == null) break;

						String key = object.key();
						if (!needsNotification(key)) continue;

						Optional<User> userByKeyPrefix = findUserByKeyPrefix(key);
						if (userByKeyPrefix.isEmpty()) {
							continue;
						}

						String text = buildNotificationMessage(key);
						sendTelegramNotification(userByKeyPrefix.get(), text);
						markAsNotified(key);

					} catch (InterruptedException e) {
						log.error("Interrupted", e);
						Thread.currentThread().interrupt();
					} catch (Exception e) {
						log.error("Failed to send notification", e);
					}
				}
			});
		}
		executorService.shutdown();
		try {
			executorService.awaitTermination(5, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			log.error("Executor interrupted during shutdown", e);
			Thread.currentThread().interrupt();
		}

		log.info("Finished sending notifications");
	}
	/**
	 * Cek apakah object perlu dikirim notifikasi.
	 */
	private boolean needsNotification(String key) {
		HeadObjectResponse header = s3Connector.s3Client().headObject(
			HeadObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.build()
		);

		return !header.metadata().containsKey("x-is-notified");
	}

	/**
	 * Cari user berdasarkan 3 huruf pertama dari key.
	 */
	private Optional<User> findUserByKeyPrefix(String key) {
		if (key.length() < 3) {
			log.warn("Key too short: {}", key);
			return Optional.empty();
		}
		String prefix = key.substring(0, 3);
		List<User> users = userRepository.findByUserCode(prefix);

		return users.stream().findFirst();
	}

	/**
	 * Buat pesan notifikasi yang lebih menarik.
	 */
	private String buildNotificationMessage(String key) {
		String kode = key.length() >= 3 ? key.substring(0, 3) : key;

		String namaWithExt = key.length() > 3 ? key.substring(3) : "";
		String nama = namaWithExt;
		int dotIndex = namaWithExt.lastIndexOf('.');
		if (dotIndex > 0) {
			nama = namaWithExt.substring(0, dotIndex);
		}

		return String.format(
			"""
				📄 *Notifikasi SLIK*
				
				✅ Kode: `%s`
				✅ Nama: `%s`
				
				Data SLIK telah *direquest*.""",
			kode, nama.replace("_", "")
		);
	}


	/**
	 * Kirim pesan Telegram.
	 */
	private void sendTelegramNotification(User user, String messageText) {
		SendMessage message = SendMessage.builder()
			.chatId(user.getChatId().toString())
			.text(messageText)
			.parseMode("Markdown")
			.build();

		try {
			getClient().execute(message);
			log.info("Notification sent to user {} ({})", user.getUserCode(), user.getChatId());
		} catch (TelegramApiException e) {
			log.error("Failed to send notification to Telegram", e);
		}
	}

	/**
	 * Tandai file S3 sebagai sudah dinotifikasi.
	 */
	private void markAsNotified(String key) {
		HeadObjectResponse response = s3Connector.s3Client().headObject(HeadObjectRequest.builder()
			.key(key)
			.bucket(bucket)
			.build());
		Map<String, String> metadataFromS3 = new ConcurrentHashMap<>(response.metadata());
		metadataFromS3.put("x-is-notified", "yes");
		CopyObjectRequest copyRequest = CopyObjectRequest.builder()
			.sourceBucket(bucket)
			.sourceKey(key)
			.destinationBucket(bucket)
			.destinationKey(key)
			.metadata(metadataFromS3)
			.metadataDirective(MetadataDirective.REPLACE)
			.build();

		s3Connector.s3Client().copyObject(copyRequest);
		log.info("Marked {} as notified", key);
	}
}
