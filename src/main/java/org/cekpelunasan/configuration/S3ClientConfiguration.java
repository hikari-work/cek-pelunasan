package org.cekpelunasan.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;

@Configuration
public class S3ClientConfiguration{

	private static final Logger log = LoggerFactory.getLogger(S3ClientConfiguration.class);

	@Value("${r2.access.key}")
	private String accessKey;

	@Value("${r2.account.id}")
	private String accountId;

	@Value("${r2.secret.key}")
	private String secretKey;

	@Value("${r2.endpoint}")
	private String endpoint;

	@Value("${r2.bucket}")
	private String bucket;

	@Bean
	public S3AsyncClient s3AsyncClient() {
		String endpointUrl = endpoint.startsWith("http") ? endpoint : buildEndpointUrl();
		log.info("Initializing S3AsyncClient with endpoint: {}", endpointUrl);
		return S3AsyncClient.builder()
			.endpointOverride(URI.create(endpointUrl))
			.credentialsProvider(createCredentialsProvider())
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
			.region(Region.US_EAST_1)
			.build();
	}

	private String buildEndpointUrl() {
		return "https://" + accountId + "." + endpoint;
	}

	private StaticCredentialsProvider createCredentialsProvider() {
		return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
	}

	public Mono<byte[]> getFile(String key) {
		try {
			GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
			return Mono.fromFuture(s3AsyncClient().getObject(request, AsyncResponseTransformer.toBytes()))
				.map(responseBytes -> responseBytes.asByteArray())
				.onErrorResume(e -> {
					log.error("Failed to download file from S3: {}", e.getMessage());
					return Mono.empty();
				});
		} catch (Exception e) {
			log.error("S3 getFile setup error for key {}: {}", key, e.getMessage());
			return Mono.empty();
		}
	}

	public Flux<String> listObjectFoundByName(String prefix) {
		try {
			ListObjectsV2Request initialRequest = ListObjectsV2Request.builder()
				.bucket(bucket)
				.prefix(prefix)
				.build();
			return Mono.fromFuture(s3AsyncClient().listObjectsV2(initialRequest))
				.expand(response -> response.isTruncated()
					? Mono.fromFuture(s3AsyncClient().listObjectsV2(
						ListObjectsV2Request.builder()
							.bucket(bucket)
							.prefix(prefix)
							.continuationToken(response.nextContinuationToken())
							.build()))
					: Mono.empty())
				.flatMap(response -> Flux.fromIterable(response.contents()))
				.map(S3Object::key)
				.onErrorResume(e -> {
					log.error("Failed to list S3 objects with prefix {}: {}", prefix, e.getMessage());
					return Flux.empty();
				});
		} catch (Exception e) {
			log.error("S3 listObjectFoundByName setup error for prefix {}: {}", prefix, e.getMessage());
			return Flux.empty();
		}
	}
}
