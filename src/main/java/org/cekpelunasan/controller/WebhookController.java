package org.cekpelunasan.controller;


import org.cekpelunasan.bot.TelegramBot;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.service.whatsapp.Routers;
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

	private final TelegramClient telegramClient;
	private final TelegramBot telegramBot;
	private final Routers routers;

	public WebhookController(@Value("${telegram.bot.token}") String botToken,
							 TelegramBot telegramBot,
							 Routers routers) {
		this.telegramClient = new OkHttpTelegramClient(botToken);
		this.telegramBot = telegramBot;
		this.routers = routers;
	}

	@PostMapping("/webhook")
	public String webhook(@RequestBody Update update) {
		telegramBot.startBot(update, telegramClient);
		return "Webhook is working!";
	}

	@PostMapping("/v2/whatsapp")
	public ResponseEntity<?> whatsappV2(@RequestBody WhatsAppWebhookDTO dto) {
		CompletableFuture.runAsync(() -> routers.handle(dto));
		return ResponseEntity.ok("OK");
	}

}
