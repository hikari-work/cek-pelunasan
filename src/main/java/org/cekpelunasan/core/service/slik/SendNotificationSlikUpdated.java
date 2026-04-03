package org.cekpelunasan.core.service.slik;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendNotificationSlikUpdated {

	@Value("${r2.bucket}")
	private String bucket;
	private volatile int lastCount = 1;

	private final S3AsyncClient s3AsyncClient;

	@Scheduled(fixedDelay = 10000)
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
	}
}
