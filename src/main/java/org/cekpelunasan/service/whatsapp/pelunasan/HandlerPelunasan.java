package org.cekpelunasan.service.whatsapp.pelunasan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.dto.whatsapp.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.whatsapp.sender.WhatsAppSenderService;
import org.cekpelunasan.service.whatsapp.dto.PelunasanDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class HandlerPelunasan {

	private static final String PELUNASAN_COMMAND_PREFIX = ".p ";
	private static final String DATA_NOT_FOUND_MESSAGE = "Data tersebut tidak ditemukan";
	private static final Pattern SPK_PATTERN = Pattern.compile("^\\d{12}$");

	private final BillService billService;
	private final PelunasanService pelunasanService;
	private final WhatsAppSenderService whatsAppSenderService;

	@Value("${admin.whatsapp}")
	private String adminWhatsApp;

	@Async
	public CompletableFuture<Void> handlePelunasan(WhatsAppWebhookDTO command) {
		return CompletableFuture.runAsync(() -> processPelunasanCommand(command));
	}

	private void processPelunasanCommand(WhatsAppWebhookDTO command) {
		try {
			logCommandDetails(command);

			if (!isValidPelunasanCommand(command)) {
				return;
			}

			String spkNumber = extractSpkNumber(command.getMessage().getText());
			if (!isValidSpkNumber(spkNumber)) {
				sendErrorMessage(command, "Format SPK tidak valid. Gunakan format: .p [12-digit SPK]");
				return;
			}

			Bills pelunasanData = fetchBillData(spkNumber);

			if (pelunasanData == null) {
				handleDataNotFound(command);
			} else {
				handleDataFound(command, pelunasanData);
			}

		} catch (Exception e) {
			log.error("Error processing pelunasan command: {}", e.getMessage(), e);
			sendErrorMessage(command, "Terjadi kesalahan sistem. Silakan coba lagi.");
		}
	}
	private boolean isValidPelunasanCommand(WhatsAppWebhookDTO command) {
		String text = command.getMessage().getText();

		if (text == null || !text.startsWith(PELUNASAN_COMMAND_PREFIX)) {
			log.debug("Invalid pelunasan command format: {}", text);
			return false;
		}

		if (text.length() <= PELUNASAN_COMMAND_PREFIX.length()) {
			log.debug("Empty SPK number in command: {}", text);
			sendErrorMessage(command, "SPK number tidak boleh kosong. Format: .p [SPK-12-digit]");
			return false;
		}

		return true;
	}

	private String extractSpkNumber(String commandText) {
		if (commandText == null || commandText.length() <= PELUNASAN_COMMAND_PREFIX.length()) {
			return "";
		}
		return commandText.substring(PELUNASAN_COMMAND_PREFIX.length()).trim();
	}

	private boolean isValidSpkNumber(String spkNumber) {
		return spkNumber != null && SPK_PATTERN.matcher(spkNumber).matches();
	}

	private Bills fetchBillData(String spkNumber) {
		try {
			log.info("Fetching bill data for SPK: {}", spkNumber);
			return billService.getBillById(spkNumber);
		} catch (Exception e) {
			log.error("Error fetching bill data for SPK {}: {}", spkNumber, e.getMessage(), e);
			return null;
		}
	}
	private void handleDataNotFound(WhatsAppWebhookDTO command) {
		log.info("Bill data not found for SPK from sender: {}", command.getCleanSenderId());

		if (isAdminUser(command)) {
			try {
				Thread.sleep(2_000L);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			updateMessageForAdmin(command, DATA_NOT_FOUND_MESSAGE);
		} else {
			sendRegularMessage(command, DATA_NOT_FOUND_MESSAGE);
		}
	}
	private void handleDataFound(WhatsAppWebhookDTO command, Bills pelunasanData) {
		try {
			log.info("Processing pelunasan for SPK: {} from sender: {}",
				pelunasanData.getNoSpk(), command.getCleanSenderId());

			PelunasanDto pelunasanDto = calculatePelunasan(pelunasanData);
			String message = generatePelunasanMessage(pelunasanDto);

			sendReactionAsync(command);

			if (isAdminUser(command)) {
				try {
					Thread.sleep(2_000L);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				updateMessageForAdmin(command, message);
			} else {
				sendRegularMessage(command, message);
			}

		} catch (Exception e) {
			log.error("Error processing pelunasan calculation: {}", e.getMessage(), e);
			sendErrorMessage(command, "Gagal menghitung pelunasan. Silakan coba lagi.");
		}
	}
	private PelunasanDto calculatePelunasan(Bills pelunasanData) {
		try {
			return pelunasanService.calculatePelunasn(pelunasanData);
		} catch (Exception e) {
			log.error("Error calculating pelunasan for SPK {}: {}",
				pelunasanData.getNoSpk(), e.getMessage(), e);
			throw new RuntimeException("Calculation failed", e);
		}
	}
	private String generatePelunasanMessage(PelunasanDto pelunasanDto) {
		try {
			return pelunasanDto.toWhatsAppMessageClean();
		} catch (Exception e) {
			log.error("Error generating pelunasan message: {}", e.getMessage(), e);
			return "Error generating message. Please contact admin.";
		}
	}
	private boolean isAdminUser(WhatsAppWebhookDTO command) {
		return adminWhatsApp != null && command.getFrom() != null &&
			command.getFrom().contains(adminWhatsApp);
	}
	private void sendReactionAsync(WhatsAppWebhookDTO command) {
		CompletableFuture.runAsync(() -> {
			try {
				whatsAppSenderService.sendReactionToMessage(
					command.buildChatId(),
					command.getMessage().getId()
				);
			} catch (Exception e) {
				log.warn("Failed to send reaction: {}", e.getMessage());
			}
		});
	}
	private void updateMessageForAdmin(WhatsAppWebhookDTO command, String message) {
		try {
			whatsAppSenderService.updateMessage(
				command.buildChatId(),
				command.getMessage().getId(),
				message
			);
			log.info("Message updated for admin user: {}", command.getCleanSenderId());
		} catch (Exception e) {
			log.error("Error updating message for admin: {}", e.getMessage(), e);
			sendRegularMessage(command, message);
		}
	}

	private void sendRegularMessage(WhatsAppWebhookDTO command, String message) {
		try {
			whatsAppSenderService.sendWhatsAppText(command.buildChatId(), message);
			log.info("Message sent to user: {}", command.getCleanSenderId());
		} catch (Exception e) {
			log.error("Error sending message: {}", e.getMessage(), e);
		}
	}
	private void sendErrorMessage(WhatsAppWebhookDTO command, String errorMessage) {
		try {
			if (isAdminUser(command)) {
				updateMessageForAdmin(command, "❌ " + errorMessage);
			} else {
				sendRegularMessage(command, "❌ " + errorMessage);
			}
		} catch (Exception e) {
			log.error("Error sending error message: {}", e.getMessage(), e);
		}
	}
	private void logCommandDetails(WhatsAppWebhookDTO command) {
		log.info("Processing pelunasan command - Sender: {}, ChatId: {}, MessageId: {}, Text: '{}'",
			command.getCleanSenderId(),
			command.getCleanChatId(),
			command.getMessage().getId(),
			command.getMessage().getText());
	}
}