package org.cekpelunasan.service.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.nio.file.Path;

@Service
public class S3Connector {

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

	private S3Client s3Client() {
		return S3Client.builder()
			.endpointOverride(URI.create("https://" + accountId + endpoint))
			.credentialsProvider(StaticCredentialsProvider.create(
				AwsBasicCredentials.create(accessKey, secretKey)
			))
			.build();
	}
	public GetObjectResponse getFiles(String fileName) {
		GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(fileName).build();
		try {
			return s3Client().getObject(request, Path.of(fileName));
		} catch (S3Exception e) {
			return null;
		}
	}
}
