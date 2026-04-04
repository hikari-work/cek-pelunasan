package org.cekpelunasan.platform.whatsapp.service.slik;

import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

@Component
public class SlikService {

	@Value("${r2.bucket}")
	private String bucket;

	private final S3AsyncClient s3AsyncClient;

	public SlikService(S3AsyncClient s3AsyncClient) {
		this.s3AsyncClient = s3AsyncClient;
	}

	public void handleSlikService(WhatsAppWebhookDTO webhookDTO) {
		String text = webhookDTO.getPayload().getBody().substring(".s ".length());
		String fileName = getMatchingItems(text, getBucketList());
		if (fileName == null) {
			return;
		}
		if (fileName.endsWith(".txt")) {
			// TODO : Generate PDF Files
		} else if (fileName.endsWith(".pdf")) {
			// TODO : Send PDF Files
		}
	}

	public List<String> getBucketList() {
		try {
			ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).build();
			ListObjectsV2Response response = s3AsyncClient.listObjectsV2(request).get();
			return response.contents().stream().map(S3Object::key).toList();
		} catch (Exception e) {
			return List.of();
		}
	}

	public String getMatchingItems(String name, List<String> items) {
		return items.stream()
			.filter(item -> item.toLowerCase().contains(name.toLowerCase()))
			.findFirst()
			.orElse(null);
	}
}
