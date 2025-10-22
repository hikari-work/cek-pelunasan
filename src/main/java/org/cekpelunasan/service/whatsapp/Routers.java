package org.cekpelunasan.service.whatsapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.service.whatsapp.hotkolek.HandleKolekCommand;
import org.cekpelunasan.service.whatsapp.jatuhbayar.JatuhBayarService;
import org.cekpelunasan.service.whatsapp.pelunasan.HandlerPelunasan;
import org.cekpelunasan.service.whatsapp.shortcut.ShortcutMessages;
import org.cekpelunasan.service.whatsapp.slik.SlikService;
import org.cekpelunasan.service.whatsapp.tabungan.TabunganService;
import org.cekpelunasan.service.whatsapp.virtualaccount.VirtualAccountHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class Routers {

	private final HandlerPelunasan handlerPelunasan;
	private final TabunganService tabunganService;
	private final JatuhBayarService jatuhBayarService;
	private final ShortcutMessages shortcutMessages;
	private final HandleKolekCommand handleKolekCommand;
	private final SlikService slikService;
	private final VirtualAccountHandler virtualAccountHandler;

	@Value("${admin.whatsapp}")
	private String adminWhatsApp;

	private static final String COMMAND_PREFIX = ".";
	private static final String ADMIN_SHORTCUT_PREFIX = "/";
	private static final String HOT_KOLEK_PATTERN = "^\\.\\d{12}(?:\\s\\d{12})*$";

	@Async
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> handle(WhatsAppWebhookDTO webhook) {
		log.info("Received webhook from={} id={}", webhook.getCleanChatId(), webhook.getMessage().getId());

		if (!isValidTextMessage(webhook)) {
			return CompletableFuture.completedFuture(null);
		}

		return CompletableFuture.runAsync(() -> processCommand(webhook));
	}

	private void processCommand(WhatsAppWebhookDTO webhook) {
		String messageText = webhook.getMessage().getText();

		if (!messageText.startsWith(COMMAND_PREFIX)) {
			return;
		}

		if (isAdminShortcut(webhook)) {
			log.info("Routing to Shortcut Service");
			shortcutMessages.sendShortcutMessage(webhook);
			return;
		}

		routeCommand(webhook, messageText);
	}

	private void routeCommand(WhatsAppWebhookDTO webhook, String text) {
		if (isHotKolekCommand(webhook)) {
			log.info("Routing to Hot Kolek Service, isGroup={}", webhook.isGroupChat());
			CompletableFuture.runAsync(() -> handleKolekCommand.handleKolekCommand(webhook));
		}
		else if (isPelunasanCommand(webhook)) {
			handlerPelunasan.handlePelunasan(webhook).join();
		}
		else if (isTabunganCommand(text)) {
			tabunganService.handleTabungan(webhook);
		}
		else if (isMinimalBayarCommand(webhook, text)) {
			log.warn("Minimal Bayar command not yet implemented");
		}
		else if (isJatuhBayarCommand(webhook, text)) {
			jatuhBayarService.handle(webhook);
		} else if (isValidSlikCommand(webhook)) {
			slikService.handleSlikService(webhook);
		} else if (isResetCommand(webhook, text)) {
			log.warn("Reset Hot Kolek command not yet implemented");
		} else if (isVirtualAccount(webhook)) {
			log.info("Routing to Virtual Account Service");
			virtualAccountHandler.handler(webhook);

		} else {
			log.debug("Unknown command: {}", text);
		}
	}

	private boolean isVirtualAccount(WhatsAppWebhookDTO webhookDTO) {
		return webhookDTO.getMessage().getText().startsWith(COMMAND_PREFIX + "va");
	}

	private boolean isValidTextMessage(WhatsAppWebhookDTO webhook) {
		return webhook.getMessage() != null && webhook.getMessage().getText() != null;
	}

	private boolean isValidSlikCommand(WhatsAppWebhookDTO webhook) {
		return isValidTextMessage(webhook) &&
			webhook.getMessage().getText().startsWith(COMMAND_PREFIX + "s");
	}

	private boolean isAdminShortcut(WhatsAppWebhookDTO webhook) {
		return isFromAdmin(webhook) &&
			webhook.getMessage().getText().startsWith(ADMIN_SHORTCUT_PREFIX);
	}

	private boolean isFromAdmin(WhatsAppWebhookDTO webhook) {
		return webhook.getFrom().contains(adminWhatsApp);
	}

	private boolean isAdminInGroup(WhatsAppWebhookDTO webhook) {
		return webhook.isGroupChat() &&
			webhook.getCleanSenderId().equals(adminWhatsApp);
	}

	// Command type checkers
	public boolean isHotKolekCommand(WhatsAppWebhookDTO webhook) {
		return isValidTextMessage(webhook) &&
			webhook.getMessage().getText().matches(HOT_KOLEK_PATTERN);
	}

	public boolean isPelunasanCommand(WhatsAppWebhookDTO webhook) {
		return isValidTextMessage(webhook) &&
			webhook.getMessage().getText().startsWith(COMMAND_PREFIX + "p");
	}

	private boolean isTabunganCommand(String text) {
		return text.startsWith(COMMAND_PREFIX + "t");
	}

	private boolean isMinimalBayarCommand(WhatsAppWebhookDTO webhook, String text) {
		return text.startsWith(COMMAND_PREFIX + "min") && isAdminInGroup(webhook);
	}

	private boolean isJatuhBayarCommand(WhatsAppWebhookDTO webhook, String text) {
		return text.startsWith(COMMAND_PREFIX + "jb") && isFromAdmin(webhook);
	}

	private boolean isResetCommand(WhatsAppWebhookDTO webhook, String text) {
		return text.startsWith(COMMAND_PREFIX + "reset") && isAdminInGroup(webhook);
	}
}