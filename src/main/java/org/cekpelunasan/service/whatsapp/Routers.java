package org.cekpelunasan.service.whatsapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class Routers {
	private static final String COMMAND_PREFIX = ".";
	private static final String HOT_KOLEK_PATTERN = "^\\.\\d{12}(?:\\s\\d{12})*$";

	private static final String SPK_NUMBER_PATTERN = "^\\d{12}$";
	private final HandleKolekCommand handleKolekCommand;


	public CompletableFuture<Void> handle(WhatsAppWebhookDTO command) {
		if (!isText(command)) {
			return CompletableFuture.completedFuture(null);
		}
		return CompletableFuture.runAsync(() -> {
			log.info("Received command from={} id={}", command.getCleanChatId(), command.getMessage().getId());
			if (!command.getMessage().getText().startsWith(COMMAND_PREFIX)) {
				return;
			}
			String text = command.getMessage().getText();
			if (isHotKolekCommand(command)) {
				log.info("Valid Hot Kolek Service, isGroup={}", command.isGroupChat());
				CompletableFuture.runAsync(() -> handleKolekCommand.handleKolekCommand(command));
				return;
			} else if (isSpkNumber(command)) {
				// TODO : Pelunasan Service and Tabungan Service
				return;
			} else {
				log.info("Invalid command format: {}", text);
			}

		});
	}

	public boolean isText(WhatsAppWebhookDTO command) {
		return command.getMessage() != null && command.getMessage().getText() != null;
	}

	public boolean isHotKolekCommand(WhatsAppWebhookDTO command) {
		return isText(command) && command.getMessage().getText().matches(HOT_KOLEK_PATTERN);
	}

	public boolean isSpkNumber(WhatsAppWebhookDTO command) {
		return isText(command) && command.getMessage().getText().matches(SPK_NUMBER_PATTERN);
	}

}
