package org.cekpelunasan.service.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


@Service
@RequiredArgsConstructor
public class PengakuanGeminiService {

	private final ObjectMapper objectMapper;

	@Value("${gemini.key}")
	private String key;

	public String askGemini(String textPrompt, String base64Image) {
		String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=%s", key);
		String requestBody = String.format("""
                {
                  "contents": [
                    {
                      "parts": [
                        {
                          "text": "%s"
                        },
                        {
                          "inline_data": {
                            "mime_type": "image/jpeg",
                            "data": "%s"
                          }
                        }
                      ]
                    }
                  ],
                  "generationConfig": {
                    "temperature": 0.4,
                    "maxOutputTokens": 2048
                  }
                }
                """, escapeJsonString(textPrompt), base64Image);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
		try {
			ResponseEntity<String> response = new RestTemplate().postForEntity(url, request, String.class);
			return extractGeminiResponse(response.getBody());
		} catch (Exception e) {
			return null;
		}

	}
	private String escapeJsonString(String input) {
		if (input == null) {
			return "";
		}
		return input.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
	private String extractGeminiResponse(String jsonResponse) {
		try {
			JsonNode rootNode = objectMapper.readTree(jsonResponse);


			JsonNode candidates = rootNode.get("candidates");
			if (candidates != null && candidates.isArray() && !candidates.isEmpty()) {
				JsonNode content = candidates.get(0).get("content");
				if (content != null) {
					JsonNode parts = content.get("parts");
					if (parts != null && parts.isArray() && !parts.isEmpty()) {
						JsonNode textNode = parts.get(0).get("text");
						if (textNode != null) {
							return textNode.asText();
						}
					}
				}
			}

			return "No response text found in API response";
		} catch (Exception e) {
			return "Error parsing API response: " + e.getMessage();
		}
	}


}
