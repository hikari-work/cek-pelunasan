package org.cekpelunasan.service.slik;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.dto.IdebDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ListUncompletedDocument {

	private static final Logger log = LoggerFactory.getLogger(ListUncompletedDocument.class);
	private static final String KTP_PREFIX = "KTP";
	private static final String ACCEPT_METADATA_KEY = "x-isaccept";

	private final S3Client s3Connector;
	private final ObjectMapper mapper;

	@Value("${r2.bucket}")
	private String bucket;

	/**
	 * List all uncompleted documents from S3 that have not been accepted
	 */
	public List<String> listUncompletedDocument() {
		ListObjectsV2Response response = listObjectsFromS3();
		logTotalObjectCount(response);

		List<String> documentIds = response.contents().parallelStream()
			.map(this::processS3Object)
			.filter(Objects::nonNull)
			.toList();

		logTotalDocumentCount(documentIds);
		return documentIds;
	}

	// ===== S3 Operations =====

	/**
	 * List all objects from S3 with KTP prefix
	 */
	private ListObjectsV2Response listObjectsFromS3() {
		ListObjectsV2Request request = buildListObjectsRequest();
		return s3Connector.listObjectsV2(request);
	}

	/**
	 * Build S3 list objects request
	 */
	private ListObjectsV2Request buildListObjectsRequest() {
		return ListObjectsV2Request.builder()
			.bucket(bucket)
			.prefix(KTP_PREFIX)
			.build();
	}

	/**
	 * Process individual S3 object
	 */
	private String processS3Object(S3Object s3Object) {
		String key = s3Object.key();
		Map<String, String> metadata = getObjectMetadata(key);

		if (isDocumentUncompleted(metadata)) {
			log.info("Data {} belum diproses", key);
			return retrieveDocumentId(key);
		}

		return null;
	}

	/**
	 * Get metadata for S3 object
	 */
	private Map<String, String> getObjectMetadata(String key) {
		HeadObjectRequest request = HeadObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.build();

		HeadObjectResponse response = s3Connector.headObject(request);
		return response.metadata();
	}

	/**
	 * Check if document is uncompleted (has accept metadata)
	 */
	private boolean isDocumentUncompleted(Map<String, String> metadata) {
		return metadata.containsKey(ACCEPT_METADATA_KEY);
	}

	// ===== Document Retrieval =====

	/**
	 * Retrieve document ID from S3 object
	 */
	private String retrieveDocumentId(String key) {
		try {
			byte[] objectBytes = downloadObjectFromS3(key);
			IdebDTO dto = deserializeToIdebDTO(objectBytes);
			String documentId = extractDocumentId(dto);
			log.info("Data {} ditemukan", documentId);
			return documentId;
		} catch (Exception e) {
			log.error("Failed to get object: {}", key, e);
			return null;
		}
	}

	/**
	 * Download object from S3
	 */
	private byte[] downloadObjectFromS3(String key) {
		GetObjectRequest request = buildGetObjectRequest(key);
		log.info("Getting object: {}", key);

		try (InputStream input = s3Connector.getObject(request)) {
			return input.readAllBytes();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Build S3 get object request
	 */
	private GetObjectRequest buildGetObjectRequest(String key) {
		return GetObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.build();
	}

	/**
	 * Deserialize byte array to IdebDTO
	 */
	private IdebDTO deserializeToIdebDTO(byte[] objectBytes) throws Exception {
		String content = new String(objectBytes, StandardCharsets.UTF_8);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper.readValue(content, IdebDTO.class);
	}

	/**
	 * Extract document ID from IdebDTO
	 */
	private String extractDocumentId(IdebDTO dto) {
		return dto.getHeader().getKodeReferensiPengguna();
	}

	// ===== Logging Methods =====

	/**
	 * Log total object count from S3
	 */
	private void logTotalObjectCount(ListObjectsV2Response response) {
		log.info("Total Object: {}", response.keyCount());
	}

	/**
	 * Log total document count processed
	 */
	private void logTotalDocumentCount(List<String> documentIds) {
		log.info("Total Data: {}", documentIds.size());
	}
}