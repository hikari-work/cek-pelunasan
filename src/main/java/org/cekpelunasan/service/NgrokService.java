package org.cekpelunasan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class NgrokService {
	private static final Logger logger = LoggerFactory.getLogger(NgrokService.class);

	private final String botToken;
	private final RestTemplate restTemplate;

	@Value("${ngrok.api.url:http://localhost:4040/api/tunnels}")
	private String ngrokApiUrl;

	public NgrokService(@Value("${telegram.bot.token}") String botToken) {
		this.restTemplate = new RestTemplate();
		this.botToken = botToken;
	}

	public boolean setupWebhookOrFallback() {
		try {
			String tunnelJson = getTunnelInfo();
			if (tunnelJson == null) {
				logger.warn("Ngrok tunnel not found/active. Switching to fallback.");
				return false;
			}

			String publicUrl = extractPublicUrl(tunnelJson);
			if (publicUrl != null) {
				return setTelegramWebHook(publicUrl);
			}
		} catch (Exception e) {
			logger.error("Error in setupWebhook: {}", e.getMessage());
		}
		return false;
	}

	public String getTunnelInfo() {
		try {
			return restTemplate.getForObject(ngrokApiUrl, String.class);
		} catch (RestClientException e) {
			logger.debug("Ngrok main port 4040 unreachable.");
			try {
				String alternativeUrl = ngrokApiUrl.replace("4040", "3030");
				return restTemplate.getForObject(alternativeUrl, String.class);
			} catch (RestClientException ex) {
				logger.debug("Ngrok alternative port 3030 unreachable.");
				return null;
			}
		}
	}

	public String extractPublicUrl(String tunnelJson) {
		try {
			JsonNode tunnelNode = new ObjectMapper().readTree(tunnelJson);
			JsonNode tunnels = tunnelNode.get("tunnels");

			if (tunnels != null && tunnels.isArray() && !tunnels.isEmpty()) {
				return tunnels.get(0).get("public_url").asText();
			}
			return null;
		} catch (Exception e) {
			logger.error("Error extracting public URL", e);
			return null;
		}
	}

	private boolean setTelegramWebHook(String publicUrl) {
		try {
			String webHookPath = "/webhook";
			String fullWebHookUrl = publicUrl + webHookPath;
			String setWebHookUrl = String.format("https://api.telegram.org/bot%s/setWebhook?url=%s",
				botToken, fullWebHookUrl);

			logger.info("Setting Webhook to: {}", fullWebHookUrl);
			String response = restTemplate.getForObject(setWebHookUrl, String.class);
			logger.info("Webhook set response: {}", response);
			return true;
		} catch (Exception e) {
			logger.error("Error setting Telegram webhook", e);
			return false;
		}
	}
	public void deleteWebhook() {
		try {
			String deleteUrl = String.format("https://api.telegram.org/bot%s/deleteWebhook", botToken);
			logger.info("Deleting Webhook to enable Long Polling...");
			String response = restTemplate.getForObject(deleteUrl, String.class);
			logger.info("Delete Webhook response: {}", response);
		} catch (Exception e) {
			logger.error("Error deleting webhook", e);
		}
	}
}