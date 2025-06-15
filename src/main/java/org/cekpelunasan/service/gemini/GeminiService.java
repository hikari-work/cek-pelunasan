package org.cekpelunasan.service.gemini;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

	@Value("${gemini.key}")
	private String key;

	private final RestTemplate restTemplate = new RestTemplate();

	public String askGemini(String query) {
		String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=%s", key);
		Map<String, Object> request = Map.of(
			"contents", List.of(
				Map.of("parts", List.of(Map.of("text", query)))
			)
		);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(request, headers);
		ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);
		Map candidate = ((List<Map>) ((Map) response.getBody()).get("candidates")).get(0);
		Map content = (Map) candidate.get("content");
		List<Map> parts = (List<Map>) content.get("parts");
		return (String) parts.get(0).get("text");
	}
}
