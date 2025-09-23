package org.cekpelunasan.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cekpelunasan.bot.TelegramBot;
import org.cekpelunasan.dto.WhatsappMessageDTO;
import org.cekpelunasan.service.whatsapp.WhatsappRouters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@RestController
public class WebhookController {

	private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
	private final TelegramClient telegramClient;
	private final TelegramBot telegramBot;
	private final WhatsappRouters whatsappRouters;
	private final ObjectMapper objectMapper;

	public WebhookController(@Value("${telegram.bot.token}") String botToken, TelegramBot telegramBot, WhatsappRouters whatsappRouters1, ObjectMapper objectMapper) {
		this.telegramClient = new OkHttpTelegramClient(botToken);
		this.telegramBot = telegramBot;
		this.whatsappRouters = whatsappRouters1;
		this.objectMapper = objectMapper;

	}

	@PostMapping("/webhook")
	public String webhook(@RequestBody Update update) {
		telegramBot.startBot(update, telegramClient);
		return "Webhook is working!";
	}
	@PostMapping("/whatsapp")
	public ResponseEntity<String> whatsapp(@RequestBody JsonNode whatsappMessage) {
		JsonNode eventNode = whatsappMessage.get("event");
		if (eventNode != null) {
			return ResponseEntity.ok("OK");
		}
		log.info("Received {}", whatsappMessage);
		WhatsappMessageDTO whatsappMessageDTO;
		try {
			whatsappMessageDTO = objectMapper.treeToValue(whatsappMessage, WhatsappMessageDTO.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		CompletableFuture.runAsync(() -> whatsappRouters.sendPelunasanOrTabungan(whatsappMessageDTO));
		// TODO : New Command Executor

		return ResponseEntity.ok("OK");
	}

	@PostMapping("/v2/whatsapp")
	public ResponseEntity<String> whatsapp(@RequestBody String whatsappMessage) {
		
	}
}
