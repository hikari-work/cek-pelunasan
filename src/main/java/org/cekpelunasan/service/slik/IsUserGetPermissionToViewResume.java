package org.cekpelunasan.service.slik;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;


@Component
public class IsUserGetPermissionToViewResume {

	private final S3Client s3Connector;
	@Value("${r2.bucket}")
	private String bucket;

	public IsUserGetPermissionToViewResume(S3Client s3Connector) {
		this.s3Connector = s3Connector;
	}
	public boolean isUserGetPermissionToViewResume(String key) {
		HeadObjectResponse response = s3Connector.headObject(
			HeadObjectRequest.builder()
				.key("KTP_" + key + ".txt")
				.bucket(bucket)
				.build()
		);
		return !response.metadata().containsKey("x-isaccept");
	}
}
