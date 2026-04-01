package org.cekpelunasan.platform.whatsapp.service.tabungan;

import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.platform.whatsapp.service.sender.WhatsAppSenderService;
import org.cekpelunasan.utils.SavingsUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class TabunganService {

	private final SavingsUtils savingsUtils;
	private final WhatsAppSenderService whatsAppSenderService;
	@Value("${admin.whatsapp}")
	private String adminWhatsApp;

	private final SavingsService savingsService;

	public TabunganService(SavingsService savingsService, SavingsUtils savingsUtils, WhatsAppSenderService whatsAppSenderService) {
		this.savingsService = savingsService;
		this.savingsUtils = savingsUtils;
		this.whatsAppSenderService = whatsAppSenderService;
	}

	@Async
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> handleTabungan(WhatsAppWebhookDTO command) {
		if (!isValidTabunganCommand(command)) {
			return CompletableFuture.completedFuture(null);
		}
		Optional<Savings> savings = savingsService.findById(command.getPayload().getBody().substring(".p ".length()));
		savings.ifPresentOrElse(saving -> {
			if (command.getFrom().contains(adminWhatsApp)) {
				String message = savingsUtils.getSavings(saving);
				whatsAppSenderService.sendReactionToMessage(command.buildChatId(), command.getPayload().getId());
				whatsAppSenderService.updateMessage(command.buildChatId(), command.getPayload().getId(), message);
			} else {
				whatsAppSenderService.sendReactionToMessage(command.buildChatId(), command.getPayload().getId());
				whatsAppSenderService.sendWhatsAppText(command.buildChatId(), savingsUtils.getSavings(saving));
			}
		}, () -> whatsAppSenderService.sendWhatsAppText(command.buildChatId(), "Data tidak ditemukan."));
		return CompletableFuture.completedFuture(null);
	}

	public boolean isValidTabunganCommand(WhatsAppWebhookDTO command) {
		return command.getPayload().getBody().matches("^\\.t \\d{12}$");
	}
}
