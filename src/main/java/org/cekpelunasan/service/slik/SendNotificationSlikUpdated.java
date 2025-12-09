package org.cekpelunasan.service.slik;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendNotificationSlikUpdated {

	@Value("${telegram.bot.token}")
	private String botToken;

	@Value("${r2.bucket}")
	private String bucket;
	private volatile int lastCount = 1;

	private final S3Client s3Client;

	@Scheduled(fixedDelay = 10000)
	public void runTest() {
		List<S3Object> objects = new ArrayList<>();

		int currentFiles = 0;
		String continuationToken = null;
		do {
			ListObjectsV2Request request = ListObjectsV2Request.builder()
					.bucket(bucket)
					.continuationToken(continuationToken)
					.build();
			ListObjectsV2Response response = s3Client.listObjectsV2(request);
			currentFiles += response.contents().size();
			continuationToken = response.nextContinuationToken();
		} while (continuationToken != null);
		if (currentFiles == lastCount) {
			log.info("PDF Count {}, Skipped", lastCount);
			objects.clear();
			return;
		}
		lastCount = currentFiles;

	}
}
