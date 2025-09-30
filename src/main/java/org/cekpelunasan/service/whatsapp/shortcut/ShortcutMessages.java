package org.cekpelunasan.service.whatsapp.shortcut;

import org.cekpelunasan.dto.whatsapp.send.MessageUpdateDTO;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.service.whatsapp.sender.WhatsAppSenderService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ShortcutMessages {

	private final WhatsAppSenderService whatsAppSenderService;

	public ShortcutMessages(WhatsAppSenderService whatsAppSenderService) {
		this.whatsAppSenderService = whatsAppSenderService;
	}

	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> sendShortcutMessage(WhatsAppWebhookDTO message) {
		String text = message.getMessage().getText();
		switch (text) {
			case "/coba" -> cobaLagiMessage(message);
			case "/kasih" -> terimakasihMessage(message);
			case "/tunggu" -> tungguMessage(message);
			case "/relog" -> relogMessage(message);
			case "/selesai" -> selesaiMessage(message);
			case "/enter" -> enterMessage(message);
			case "/input" -> inputMessage(message);
			case "/display" -> displayMessage(message);

		}
		return CompletableFuture.completedFuture(null);
	};

	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> cobaLagiMessage(WhatsAppWebhookDTO message) {
		return CompletableFuture.runAsync(() -> {
			whatsAppSenderService.updateMessage(message.buildChatId(), message.getMessage().getId(), "silahkan bisa dicoba dembali kak");
		});
	}
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> terimakasihMessage(WhatsAppWebhookDTO message) {
		return CompletableFuture.runAsync(() -> {
			whatsAppSenderService.updateMessage(message.buildChatId(), message.getMessage().getId(), "terima kasih kembali kak 🙏");
		});
	}
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> tungguMessage(WhatsAppWebhookDTO message) {
		return CompletableFuture.runAsync(() -> {
			whatsAppSenderService.updateMessage(message.buildChatId(), message.getMessage().getId(), "baik, mohon ditunggu kak");
		});
	}

	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> relogMessage(WhatsAppWebhookDTO message) {
		return CompletableFuture.runAsync(() -> {
			whatsAppSenderService.updateMessage(message.buildChatId(), message.getMessage().getId(), "silahkan usernya relogin terlebih dahulu kak, kemudian bisa dicoba kembali");
		});
	}

	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> selesaiMessage(WhatsAppWebhookDTO message) {
		return CompletableFuture.runAsync(() -> {
			whatsAppSenderService.updateMessage(message.buildChatId(), message.getMessage().getId(), "kalo sudah selesai kami diinfo ya kak");
		});
	}
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> enterMessage(WhatsAppWebhookDTO message) {
		return CompletableFuture.runAsync(() -> {
			whatsAppSenderService.updateMessage(message.buildChatId(), message.getMessage().getId(), "enter lagi kak, kalo sudah kami diinfo ya kak");
		});
	}
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> inputMessage(WhatsAppWebhookDTO message) {
		return CompletableFuture.runAsync(() -> {
			whatsAppSenderService.updateMessage(message.buildChatId(), message.getMessage().getId(), "baik kak sudah kami input ya 🙏");
		});
	}
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> displayMessage(WhatsAppWebhookDTO message) {
		return CompletableFuture.runAsync(() -> {
			whatsAppSenderService.updateMessage(message.buildChatId(), message.getMessage().getId(), "untuk display tersebut sudah dibebaskan ya kak, bisa dicoba kembali 🙏");
		});
	}

}
