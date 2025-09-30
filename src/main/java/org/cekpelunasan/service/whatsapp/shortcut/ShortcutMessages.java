package org.cekpelunasan.service.whatsapp.shortcut;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.service.whatsapp.sender.WhatsAppSenderService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class ShortcutMessages {

	private final WhatsAppSenderService whatsAppSenderService;
	private final Map<String, String> shortcutResponses;

	public ShortcutMessages(WhatsAppSenderService whatsAppSenderService) {
		this.whatsAppSenderService = whatsAppSenderService;
		this.shortcutResponses = Map.ofEntries(
			Map.entry("/coba", "silahkan bisa dicoba kembali kak"),
			Map.entry("/kasih", "terima kasih kembali kak 🙏"),
			Map.entry("/tunggu", "baik, mohon ditunggu kak"),
			Map.entry("/relog", "silahkan usernya relogin terlebih dahulu kak, kemudian bisa dicoba kembali"),
			Map.entry("/selesai", "kalo sudah selesai kami diinfo ya kak"),
			Map.entry("/enter", "enter lagi kak, kalo sudah kami diinfo ya kak"),
			Map.entry("/input", "baik kak sudah kami input ya 🙏"),
			Map.entry("/display", "untuk display tersebut sudah dibebaskan ya kak, bisa dicoba kembali 🙏"),
			Map.entry("/terima", "terimakasih kak 🙏")
		);
	}

	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> sendShortcutMessage(WhatsAppWebhookDTO message) {
		log.info("Goto ShortCutMessage");
		String text = message.getMessage().getText();
		String response = shortcutResponses.get(text);

		if (response != null) {
			return CompletableFuture.runAsync(() ->
				whatsAppSenderService.updateMessage(
					message.buildChatId(),
					message.getMessage().getId(),
					response
				)
			);
		}

		log.warn("Unknown shortcut command: {}", text);
		return CompletableFuture.completedFuture(null);
	}
}