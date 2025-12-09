package org.cekpelunasan.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for S3 Client.
 * <p>
 * This class configures the {@link S3Client} for interacting with S3-compatible
 * storage services (e.g., Cloudflare R2).
 * It also provides utility methods for file retrieval and listing.
 * </p>
 */
@Configuration
public class S3ClientConfiguration {

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

	/**
	 * Creates an {@link S3Client} bean for interacting with Cloudflare R2 or AWS
	 * S3.
	 *
	 * @return The configured {@link S3Client}.
	 * @throws RuntimeException if initialization fails.
	 */
	@Bean
	public S3Client s3Client() {
		try {
			String endpointUrl = endpoint.startsWith("http") ? endpoint : buildEndpointUrl();
			log.info("Initializing S3Client with endpoint: {}", endpointUrl);

			S3Client client = S3Client.builder()
					.endpointOverride(URI.create(endpointUrl))
					.credentialsProvider(createCredentialsProvider())
					.serviceConfiguration(S3Configuration.builder()
							.pathStyleAccessEnabled(true)
							.build())
					.region(Region.US_EAST_1)
					.build();

			log.info("S3Client successfully initialized");
			return client;
		} catch (Exception e) {
			log.error("Failed to initialize S3Client", e);
			throw new RuntimeException("S3Client initialization failed", e);
		}
	}

	/**
	 * Builds the S3 endpoint URL using the account ID and endpoint base URL.
	 *
	 * @return The constructed endpoint URL.
	 */
	private String buildEndpointUrl() {
		return "https://" + accountId + "." + endpoint;
	}

	/**
	 * Creates a {@link StaticCredentialsProvider} using the configured access and
	 * secret keys.
	 *
	 * @return The credentials provider.
	 */
	private StaticCredentialsProvider createCredentialsProvider() {
		AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
		return StaticCredentialsProvider.create(credentials);
	}

	/**
	 * Retrieves a file from the S3 bucket by its key.
	 *
	 * @param key The key (path) of the file in the bucket.
	 * @return The file content as a byte array.
	 * @throws RuntimeException if the download fails.
	 */
	public byte[] getFile(String key) {
		GetObjectRequest request = GetObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.build();
		try (ResponseInputStream<GetObjectResponse> response = s3Client().getObject(request)) {
			return response.readAllBytes();
		} catch (Exception e) {
			log.error("Failed to download file from S3", e);
			throw new RuntimeException("Failed to download file from S3", e);
		}
	}

	/**
	 * Lists object keys in the S3 bucket that match a given prefix.
	 *
	 * @param prefix The prefix to filter object keys.
	 * @return A list of matching object keys.
	 */
	public List<String> listObjectFoundByName(String prefix) {
		List<String> allObject = new ArrayList<>();
		String continuationToken = null;
		boolean isTruncated = true;

		while (isTruncated) {
			ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
					.bucket(bucket)
					.prefix(prefix);

			if (continuationToken != null) {
				requestBuilder.continuationToken(continuationToken);
			}

			ListObjectsV2Response response = s3Client().listObjectsV2(requestBuilder.build());

			if (response.contents() != null) {
				for (S3Object object : response.contents()) {
					allObject.add(object.key());
				}
			}

			continuationToken = response.nextContinuationToken();
			isTruncated = response.isTruncated();
		}
		return allObject;
	}
}