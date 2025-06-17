package org.cekpelunasan.service.slik;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.MetadataDirective;

import java.util.Collections;
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

	public void generateMetadata(String key) {
		Map<String, String> metadata = Map.of("x-isAccept", "yes");
		CopyObjectRequest copyObjectRequest = CopyObjectRequest.builder()
			.sourceBucket(bucket)
			.sourceKey("KTP_" + key + ".txt")
			.destinationBucket(bucket)
			.destinationKey("KTP_" + key + ".txt")
			.metadata(metadata)
			.metadataDirective(MetadataDirective.REPLACE)
			.build();
		log.info("Object ditemukan {}", copyObjectRequest.sourceKey());
		s3Connector.s3Client().copyObject(copyObjectRequest);
		log.info("Metadata Uploaded for {}", key);
	}
	public void deleteMetadata(String key) {
		CopyObjectRequest copyObjectRequest = CopyObjectRequest.builder()
			.sourceBucket(bucket)
			.sourceKey("KTP_" + key + ".txt")
			.destinationBucket(bucket)
			.destinationKey("KTP_" + key + ".txt")
			.metadata(Collections.emptyMap())
			.metadataDirective(MetadataDirective.REPLACE)
			.build();
		log.info("Object ditemukan {}", copyObjectRequest.sourceKey());
		s3Connector.s3Client().copyObject(copyObjectRequest);
		log.info("Metadata Deleted for {}", key);
	}
}
