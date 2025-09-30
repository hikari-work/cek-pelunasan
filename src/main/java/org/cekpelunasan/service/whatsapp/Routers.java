package org.cekpelunasan.service.whatsapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.service.whatsapp.hotkolek.HandleKolekCommand;
import org.cekpelunasan.service.whatsapp.jatuhbayar.JatuhBayarService;
import org.cekpelunasan.service.whatsapp.pelunasan.HandlerPelunasan;
import org.cekpelunasan.service.whatsapp.shortcut.ShortcutMessages;
import org.cekpelunasan.service.whatsapp.tabungan.TabunganService;
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
	@Value("${admin.whatsapp}")
	private String adminWhatsApp;

	private static final String COMMAND_PREFIX = ".";
	private static final String HOT_KOLEK_PATTERN = "^\\.\\d{12}(?:\\s\\d{12})*$";

	private static final String SPK_NUMBER_PATTERN = "^\\d{12}$";
	private final HandleKolekCommand handleKolekCommand;


	@Async
	@SuppressWarnings("UnusedReturnValue")
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
			} else if (text.startsWith("/") && command.getFrom().contains(adminWhatsApp)) {
				log.info("Handle Auto Edit");
				shortcutMessages.sendShortcutMessage(command);
			} else if (isPelunasanCommand(command)) {
				CompletableFuture<CompletableFuture<Void>> completableFutureCompletableFuture = CompletableFuture.supplyAsync(() -> handlerPelunasan.handlePelunasan(command));
				completableFutureCompletableFuture.join();
			} else if (text.startsWith(COMMAND_PREFIX + "t")){
				tabunganService.handleTabungan(command);
			} else if (text.startsWith(COMMAND_PREFIX + "min") && command.isGroupChat() && command.getCleanSenderId().equals(adminWhatsApp)) {
				// TODO : Send Minimal Bayar
			} else if (text.startsWith(COMMAND_PREFIX + "jb") && command.getFrom().contains(adminWhatsApp)) {
				jatuhBayarService.handle(command);
			} else if (text.startsWith(COMMAND_PREFIX + "reset") && command.isGroupChat() && command.getCleanSenderId().equals(adminWhatsApp)) {
				// TODO : Reset Hot Kolek
			}
		});
	}

	public boolean isText(WhatsAppWebhookDTO command) {
		return command.getMessage() != null && command.getMessage().getText() != null;
	}

	public boolean isHotKolekCommand(WhatsAppWebhookDTO command) {
		return isText(command) && command.getMessage().getText().matches(HOT_KOLEK_PATTERN);
	}


	public boolean isPelunasanCommand(WhatsAppWebhookDTO command) {
		return isText(command) && command.getMessage().getText().startsWith(".p");
	}

}
