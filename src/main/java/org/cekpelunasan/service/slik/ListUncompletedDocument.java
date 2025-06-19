package org.cekpelunasan.service.slik;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.dto.IdebDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ListUncompletedDocument {

	private static final Logger log = LoggerFactory.getLogger(ListUncompletedDocument.class);
	private final ObjectMapper mapper = new ObjectMapper();

	private final S3Connector s3Connector;
	@Value("${r2.bucket}")
	private String bucket;
	public List<String> listUncompletedDocument() {
		ListObjectsV2Request request = ListObjectsV2Request.builder()
			.bucket(bucket)
			.prefix("KTP")
			.build();
		ListObjectsV2Response response = s3Connector.s3Client().listObjectsV2(request);
		log.info("Total Object: {}", response.keyCount());
		List<String> objects = response.contents().parallelStream()
			.map(object -> {
				String key = object.key();
				HeadObjectResponse headResponse = s3Connector.s3Client().headObject(
					HeadObjectRequest.builder()
						.bucket(bucket)
						.key(key)
						.build()
				);
				Map<String, String> metadata = headResponse.metadata();
				if (metadata.containsKey("x-isaccept")) {
					log.info("Data {} belum diproses", key);
					GetObjectRequest objectRequest = GetObjectRequest.builder()
						.bucket(bucket)
						.key(key)
						.build();
					log.info("Getting object: {}", objectRequest.key());
					try (InputStream input = s3Connector.s3Client().getObject(objectRequest)){
						byte[] bytes = input.readAllBytes();
						String content = new String(bytes, StandardCharsets.UTF_8);
						mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
						IdebDTO dto = mapper.readValue(content, IdebDTO.class);
						log.info("Data {} ditemukan", dto.getHeader().getKodeReferensiPengguna());
						return dto.getHeader().getKodeReferensiPengguna();
					} catch (Exception e) {
						log.error("Failed to get object: {}", key, e);
						return null;
					}
				}
				return null;

			}).filter(Objects::nonNull)
			.toList();
		log.info("Total Data: {}", objects.size());
		return objects;
	}
}
