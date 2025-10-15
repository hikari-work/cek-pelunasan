package org.cekpelunasan.service.whatsapp.slik;

import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.service.slik.S3Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.*;

import java.util.List;

@Component
public class SlikService {

	@Value("${r2.bucket}")
	private String bucket;

	private final S3Connector s3Connector;

	public SlikService(S3Connector s3Connector) {
		this.s3Connector = s3Connector;
	}

	public void handleSlikService(WhatsAppWebhookDTO webhookDTO) {
		String text = webhookDTO.getMessage().getText().substring(".s ".length());
		String fileName = getMatchingItems(text, getBucketList());
		if (fileName == null) {
			return;
		}
		if (fileName.endsWith(".txt")) {
			// TODO : Generate PDF Files
			return;
		} else if (fileName.endsWith(".pdf")) {
			// TODO : Send PDF Files
			return;
		}
	}

	public List<String> getBucketList() {
		ListObjectsV2Request request = ListObjectsV2Request.builder()
			.bucket(bucket)
			.build();
		ListObjectsV2Response response = s3Connector.s3Client().listObjectsV2(request);
		return response.contents().stream().map(S3Object::key).toList();
	}
	public String getMatchingItems(String name, List<String> items) {
		return items.stream()
			.filter(item -> item.toLowerCase().contains(name.toLowerCase()))
			.findFirst()
			.orElse(null);
	}
	public byte[] getObjectBytes(String bytesName) {
		GetObjectRequest request = GetObjectRequest.builder()
			.bucket(bucket)
			.key(bytesName)
			.build();
		ResponseBytes<GetObjectResponse> objectAsBytes = s3Connector.s3Client().getObjectAsBytes(request);
		return objectAsBytes.asByteArray();
	}

}
