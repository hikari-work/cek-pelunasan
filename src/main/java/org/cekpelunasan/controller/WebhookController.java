package org.cekpelunasan.controller;


import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cekpelunasan.bot.TelegramBot;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.service.whatsapp.Routers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

/*
The main controller for handling incoming webhook requests.
Using telegram or WhatsApp webhook after filter in Servlet.
 */

@RestController
@RequiredArgsConstructor
public class WebhookController {

	private final TelegramBot telegramBot;
	private final Routers routers;

	@PostMapping("/webhook")
	public String webhook(@RequestBody Update update) {
		telegramBot.startBot(update);
		return "Webhook is working!";
	}

	@PostMapping("/v2/whatsapp")
	public ResponseEntity<?> whatsappV2(@RequestBody WhatsAppWebhookDTO dto) {
		CompletableFuture.runAsync(() -> routers.handle(dto));
		return ResponseEntity.ok("OK");
	}

}
