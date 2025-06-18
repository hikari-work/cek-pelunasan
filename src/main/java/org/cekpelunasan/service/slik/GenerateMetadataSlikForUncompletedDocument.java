package org.cekpelunasan.service.slik;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;

import java.util.HashMap;
import java.util.Map;

@Component
public class GenerateMetadataSlikForUncompletedDocument {

	private static final Logger log = LoggerFactory.getLogger(GenerateMetadataSlikForUncompletedDocument.class);

	private final S3Connector s3Connector;

	@Value("${r2.bucket}")
	private String bucket;

	public GenerateMetadataSlikForUncompletedDocument(S3Connector s3Connector) {
		this.s3Connector = s3Connector;
	}

	/**
	 * Tambah metadata x-isAccept = yes
	 */
	public void generateMetadata(String key) {
		String objectKey = "KTP_" + key + ".txt";

		Map<String, String> metadata = getObjectMetadata(objectKey);
		metadata.put("x-isaccept", "yes");

		copyObjectWithMetadata(objectKey, metadata);

		log.info("Metadata uploaded for key: {}", key);
	}

	/**
	 * Hapus metadata x-isAccept
	 */
	public void deleteMetadata(String key) {
		String objectKey = "KTP_" + key + ".txt";

		Map<String, String> metadata = getObjectMetadata(objectKey);
		metadata.remove("x-isaccept");

		copyObjectWithMetadata(objectKey, metadata);

		log.info("Metadata deleted for key: {}", key);
	}

	/**
	 * Ambil metadata dari S3 lalu bungkus di HashMap baru (biar bisa diedit)
	 */
	private Map<String, String> getObjectMetadata(String objectKey) {
		HeadObjectResponse headResponse = s3Connector.s3Client().headObject(
			HeadObjectRequest.builder()
				.bucket(bucket)
				.key(objectKey)
				.build()
		);
		return new HashMap<>(headResponse.metadata());
	}

	/**
	 * Salin object dengan metadata baru
	 */
	private void copyObjectWithMetadata(String objectKey, Map<String, String> metadata) {
		CopyObjectRequest copyRequest = CopyObjectRequest.builder()
			.sourceBucket(bucket)
			.sourceKey(objectKey)
			.destinationBucket(bucket)
			.destinationKey(objectKey)
			.metadata(metadata)
			.metadataDirective(MetadataDirective.REPLACE)
			.build();

		log.info("Copying object with new metadata: {}", objectKey);
		s3Connector.s3Client().copyObject(copyRequest);
	}
}
