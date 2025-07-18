package org.cekpelunasan.controller;

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

@RestController
public class WebhookController {

	private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
	private final TelegramClient telegramClient;
	private final TelegramBot telegramBot;
	private final WhatsappRouters whatsappRouters;

	public WebhookController(@Value("${telegram.bot.token}") String botToken, TelegramBot telegramBot, WhatsappRouters whatsappRouters1) {
		this.telegramClient = new OkHttpTelegramClient(botToken);
		this.telegramBot = telegramBot;
		this.whatsappRouters = whatsappRouters1;
	}

	@PostMapping("/webhook")
	public String webhook(@RequestBody Update update) {
		telegramBot.startBot(update, telegramClient);
		return "Webhook is working!";
	}
	@PostMapping("/whatsapp")
	public ResponseEntity<String> whatsapp(@RequestBody WhatsappMessageDTO whatsappMessageDTO) {
		log.info("Updated {}", whatsappMessageDTO);
		whatsappRouters.sendPelunasanOrTabungan(whatsappMessageDTO);
		return ResponseEntity.ok("OK");
	}
}
