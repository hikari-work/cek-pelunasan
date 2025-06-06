package org.cekpelunasan.service.slik;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.List;

@Service
public class S3Connector {

	private static final Logger log = LoggerFactory.getLogger(S3Connector.class);
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

private S3Client s3Client;

@PostConstruct
public void initializeS3Client() {
    try {
        log.info("Initializing S3Client with endpoint: https://{}.{}", accountId, endpoint);
        s3Client = S3Client.builder()
            .endpointOverride(URI.create("https://" + accountId + "." + endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            ))
            .region(software.amazon.awssdk.regions.Region.US_EAST_1)
            .build();
        log.info("S3Client successfully initialized");
    } catch (Exception e) {
        log.error("Failed to initialize S3Client:");
    }
}

public S3Client s3Client() {
    if (s3Client == null) {
        initializeS3Client();
    }
    return s3Client;
}

	public byte[] getFile(String fileName) {
		log.info("Searching for Files {}", fileName);
		GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(fileName).build();
		try {
			return s3Client().getObjectAsBytes(request).asByteArray();
		} catch (S3Exception e) {
			log.info("Not Found: {}", e.awsErrorDetails().errorMessage());
			return null;
		}
	}
	public List<String> listObjectFoundByName(String prefix) {
		ListObjectsV2Request request = ListObjectsV2Request.builder()
			.bucket(bucket)
			.prefix(prefix)
			.build();
		ListObjectsV2Response response = s3Client.listObjectsV2(request);
		return response.contents()
			.stream()
			.map(S3Object::key)
			.toList();
	}

}