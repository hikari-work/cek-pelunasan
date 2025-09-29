package org.cekpelunasan.service.whatsapp.sender;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.dto.whatsapp.send.BaseMessageRequestDTO;
import org.cekpelunasan.dto.whatsapp.send.GenericResponseDTO;
import org.cekpelunasan.dto.whatsapp.send.MessageActionDTO;
import org.cekpelunasan.dto.whatsapp.send.SendFileMessageDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;



@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppSender {

	private final RestTemplate rest = new RestTemplate();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${whatsapp.gateway.url}")
	private String baseUrl;

	@Value("${whatsapp.gateway.username}")
	private String username;

	@Value("${whatsapp.gateway.password}")
	private String password;

	public String buildUrl(TypeMessage messageType) {
		return switch (messageType) {
			case TEXT -> baseUrl + "/send/message";
			case IMAGE -> baseUrl + "/send/image";
			case VIDEO -> baseUrl + "/send/video";
			case REACTION -> baseUrl + "/message/{message_id}/reaction";
			case UPDATE -> baseUrl + "/message/{message_id}/update";
			case DELETE -> baseUrl + "/message/{message_id}/delete";
			case FILE -> baseUrl + "/send/file";
		};
	}

	public HttpHeaders headers() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(username, password);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
	public HttpHeaders headersMultiPart() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(username, password);
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		return headers;
	}

	public ResponseEntity<GenericResponseDTO> request(String url, BaseMessageRequestDTO messageRequestDTO) {
		try {
			String jsonBody = objectMapper.writeValueAsString(messageRequestDTO);
			log.warn(">>> SENDING TEXT via WhatsAppSender, url={}, body={}", url, jsonBody);

			HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers());

			return rest.exchange(url, HttpMethod.POST, entity, GenericResponseDTO.class);

		} catch (Exception e) {
			log.error("Error sending WhatsApp request", e);
			throw new RuntimeException("Failed to send WhatsApp message", e);
		}
	}
	public ResponseEntity<GenericResponseDTO> request(String url, MessageActionDTO messageActionDTO) {
		try {
			String jsonBody = objectMapper.writeValueAsString(messageActionDTO);
			url = url.replace("{message_id}", messageActionDTO.getMessageId());
			HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers());

			return rest.exchange(url, HttpMethod.POST, entity, GenericResponseDTO.class);

		} catch (Exception e) {
			log.error("Error sending WhatsApp request", e);
			throw new RuntimeException("Failed to send WhatsApp message", e);
		}
	}
	public ResponseEntity<GenericResponseDTO> requestMultiPart(String url, SendFileMessageDTO form, TypeMessage type) {
		try {
			MultiValueMap<String, Object> formData = getFormData(form, type);
			HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(formData, headersMultiPart());
			log.warn(">>> SENDING FILE via WhatsAppSender, url={}, phone={}, caption={}, filename={}",
				url, form.getPhone(), form.getCaption(), form.getFileName());
			return rest.exchange(url, HttpMethod.POST, entity, GenericResponseDTO.class);
		} catch (Exception e) {
			log.error("Error sending WhatsApp request", e);
			throw new RuntimeException("Failed to send WhatsApp message", e);
		}

	}
	private static MultiValueMap<String, Object> getFormData(SendFileMessageDTO form, TypeMessage type) {
		MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
		formData.add("phone", form.getPhone());
		formData.add("caption", form.getCaption());
		formData.add("isForwarded", form.getIsForwarded());
		formData.add("duration", form.getDuration());

		switch (type) {
			case IMAGE -> addFileOrUrl(formData, "image", "image", form);
			case VIDEO -> addFileOrUrl(formData, "video", "video_url", form);
			case FILE -> addFile(formData, "file", form);
			default -> throw new IllegalArgumentException("Unsupported file type: " + type);
		}

		return formData;
	}

	private static void addFileOrUrl(MultiValueMap<String, Object> formData, String fileKey, String urlKey, SendFileMessageDTO form) {
		if (form.getFileBytes() != null) {
			addFile(formData, fileKey, form);
		} else {
			String url = fileKey.equals("image") ? form.getImageUrl() : form.getVideoUrl();
			formData.add(urlKey, url);
		}
	}

	private static void addFile(MultiValueMap<String, Object> formData, String key, SendFileMessageDTO form) {
		ByteArrayResource resource = new ByteArrayResource(form.getFileBytes()) {
			@Override
			public String getFilename() {
				return form.getFileName();
			}
		};
		formData.add(key, resource);
	}

	public boolean isSuccess(ResponseEntity<GenericResponseDTO> response) {
		return response != null &&
			response.getStatusCode().is2xxSuccessful() &&
			response.getBody() != null &&
			"SUCCESS".equals(response.getBody().getCode());
	}

}