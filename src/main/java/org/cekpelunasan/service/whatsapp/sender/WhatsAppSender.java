package org.cekpelunasan.service.whatsapp.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.dto.whatsapp.send.BaseMessageRequestDTO;
import org.cekpelunasan.dto.whatsapp.send.GenericResponseDTO;
import org.cekpelunasan.dto.whatsapp.send.MessageActionDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
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
			default -> null;
		};
	}

	public HttpHeaders headers() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(username, password);
		headers.setContentType(MediaType.APPLICATION_JSON);
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

	public boolean isSuccess(ResponseEntity<GenericResponseDTO> response) {
		return response != null &&
			response.getStatusCode().is2xxSuccessful() &&
			response.getBody() != null &&
			"SUCCESS".equals(response.getBody().getCode());
	}
}