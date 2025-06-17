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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ListUncompletedDocument {

	private static final Logger log = LoggerFactory.getLogger(ListUncompletedDocument.class);
	private final ObjectMapper mapper = new ObjectMapper();

	private final S3Connector s3Connector;
	@Value("${r2.bucket}")
	private String bucket;
	public List<String> listUncompletedDocument() {
		List<String> result = new ArrayList<>();
		ListObjectsV2Request request = ListObjectsV2Request.builder()
			.bucket(bucket)
			.prefix("KTP")
			.build();
		ListObjectsV2Response response = s3Connector.s3Client().listObjectsV2(request);
		log.info("Total Object: {}", response.keyCount());
		for (S3Object object : response.contents()) {
			String key = object.key();
			HeadObjectResponse header = s3Connector.s3Client().headObject(
				HeadObjectRequest.builder()
					.bucket(bucket)
					.key(key)
					.build()
			);
			Map<String, String> metadata = header.metadata();
			if (metadata.containsKey("x-isaccept")) {
				log.info("Ada Yang Belum Diproses: {}", key);
				GetObjectRequest request1 = GetObjectRequest.builder()
					.bucket(bucket)
					.key(key)
					.build();
				log.info("Mengambil Object: {}", request1.key());
				try (InputStream s3Object = s3Connector.s3Client().getObject(request1)){
					byte[] bytes = s3Object.readAllBytes();
					String content = new String(bytes, StandardCharsets.UTF_8);
					mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
					try {
						IdebDTO idebDTO = mapper.readValue(content, IdebDTO.class);
						log.info("Data Ditemukan: {}", idebDTO.getHeader().getKodeReferensiPengguna());
						result.add(idebDTO.getHeader().getKodeReferensiPengguna());
					} catch (Exception e) {
						log.error("Failed");
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		return result;
	}
}
