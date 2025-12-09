package org.cekpelunasan.controller;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.bot.TelegramBot;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.service.whatsapp.Routers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.concurrent.CompletableFuture;

/*
The main controller for handling incoming webhook requests.
Using telegram or WhatsApp webhook after filter in Servlet.
 */

/**
 * REST Controller for handling webhook callbacks.
 * <p>
 * This controller processes incoming webhooks from Telegram and WhatsApp.
 * </p>
 */
@RestController
@RequiredArgsConstructor
public class WebhookController {

	private final TelegramBot telegramBot;
	private final Routers routers;

	/**
	 * Endpoint to receive Telegram webhooks.
	 *
	 * @param update The {@link Update} payload from Telegram.
	 * @return A confirmation string.
	 */
	@PostMapping("/webhook")
	public String webhook(@RequestBody Update update) {
		telegramBot.startBot(update);
		return "Webhook is working!";
	}

	/**
	 * Endpoint to receive WhatsApp webhooks (v2).
	 *
	 * @param dto The {@link WhatsAppWebhookDTO} payload from WhatsApp.
	 * @return A {@link ResponseEntity} with status OK.
	 */
	@PostMapping("/v2/whatsapp")
	public ResponseEntity<?> whatsappV2(@RequestBody WhatsAppWebhookDTO dto) {
		CompletableFuture.runAsync(() -> routers.handle(dto));
		return ResponseEntity.ok("OK");
	}

}
