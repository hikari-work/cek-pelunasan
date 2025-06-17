package org.cekpelunasan.service.slik;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;


@Component
public class IsUserGetPermissionToViewResume {

	private final S3Connector s3Connector;
	@Value("${r2.bucket}")
	private String bucket;

	public IsUserGetPermissionToViewResume(S3Connector s3Connector) {
		this.s3Connector = s3Connector;
	}
	public boolean isUserGetPermissionToViewResume(String key) {
		HeadObjectResponse response = s3Connector.s3Client().headObject(
			HeadObjectRequest.builder()
				.key("KTP_" + key + ".txt")
				.bucket(bucket)
				.build()
		);
		return !response.metadata().containsKey("x-isaccept");
	}
}
