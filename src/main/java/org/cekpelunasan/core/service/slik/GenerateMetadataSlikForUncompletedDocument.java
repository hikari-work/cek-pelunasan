package org.cekpelunasan.core.service.slik;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;

import java.util.HashMap;
import java.util.Map;

@Component
public class GenerateMetadataSlikForUncompletedDocument {

	private static final Logger log = LoggerFactory.getLogger(GenerateMetadataSlikForUncompletedDocument.class);

	private final S3AsyncClient s3AsyncClient;

	@Value("${r2.bucket}")
	private String bucket;

	public GenerateMetadataSlikForUncompletedDocument(S3AsyncClient s3AsyncClient) {
		this.s3AsyncClient = s3AsyncClient;
	}

	public void generateMetadata(String key) {
		String objectKey = "KTP_" + key + ".txt";
		Map<String, String> metadata = getObjectMetadata(objectKey);
		metadata.put("x-isaccept", "yes");
		copyObjectWithMetadata(objectKey, metadata);
		log.info("Metadata uploaded for key: {}", key);
	}

	public void deleteMetadata(String key) {
		String objectKey = "KTP_" + key + ".txt";
		Map<String, String> metadata = getObjectMetadata(objectKey);
		metadata.remove("x-isaccept");
		copyObjectWithMetadata(objectKey, metadata);
		log.info("Metadata deleted for key: {}", key);
	}

	private Map<String, String> getObjectMetadata(String objectKey) {
		try {
			HeadObjectResponse headResponse = s3AsyncClient.headObject(
				HeadObjectRequest.builder().bucket(bucket).key(objectKey).build()
			).get();
			return new HashMap<>(headResponse.metadata());
		} catch (Exception e) {
			log.error("Failed to get metadata for {}: {}", objectKey, e.getMessage());
			return new HashMap<>();
		}
	}

	private void copyObjectWithMetadata(String objectKey, Map<String, String> metadata) {
		try {
			CopyObjectRequest copyRequest = CopyObjectRequest.builder()
				.sourceBucket(bucket)
				.sourceKey(objectKey)
				.destinationBucket(bucket)
				.destinationKey(objectKey)
				.metadata(metadata)
				.metadataDirective(MetadataDirective.REPLACE)
				.build();
			log.info("Copying object with new metadata: {}", objectKey);
			s3AsyncClient.copyObject(copyRequest).get();
		} catch (Exception e) {
			log.error("Failed to copy object with metadata for {}: {}", objectKey, e.getMessage());
		}
	}
}
