package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.slik.GeneratePDF;
import org.cekpelunasan.service.slik.S3Connector;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;

@Component
public class SlikCommand implements CommandProcessor {


	private final S3Connector s3Connector;
	private final GeneratePDF generatePDF;
	private final AuthorizedChats authorizedChats1;
	private final MessageTemplate messageTemplate;

	public SlikCommand(S3Connector s3Connector, GeneratePDF generatePDF, AuthorizedChats authorizedChats1, MessageTemplate messageTemplate) {
		this.s3Connector = s3Connector;
		this.generatePDF = generatePDF;
		this.authorizedChats1 = authorizedChats1;
		this.messageTemplate = messageTemplate;
	}

	@Override
	public String getCommand() {
		return "/slik";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (!authorizedChats1.isAuthorized(chatId)) {
				sendMessage(chatId, messageTemplate.unathorizedMessage(), telegramClient);
				return;
			}
			String filename = text.replace("/slik ", "").trim();
			if (filename.isEmpty()) {
				sendMessage(chatId, "No KTP Harus Diisi", telegramClient);
				return;
			}
			if (filename.length() != 16) {
				sendMessage(chatId, "KTP Harus 16 Digit", telegramClient);
				return;
			}
			Message message = sendNotification(chatId, "Mengambil Data KTP", telegramClient);
			byte[] files = s3Connector.getFile("KTP_" + filename + ".txt");
			if (files == null) {
				System.out.println("File found: ");
				editMessage(chatId, message.getMessageId(), "Data KTP `" + filename +"` tidak ada", telegramClient);
			}
			String s = generatePDF.sendBytesWithRestTemplate(files, filename + ".txt");
			if (s == null || s.isEmpty()) {
				System.out.println("File not found: " + filename);
				editMessage(chatId, message.getMessageId(), "Data KTP `" + filename +"` tidak ada", telegramClient);
				return;
			}
			byte[] bytes = generatePDF.convertHtmlToPdf(s);
			editMessage(chatId, message.getMessageId(), "Data KTP `" + filename +"` Ditemukan....", telegramClient);
			if (files == null || bytes.length == 0) {
				System.out.println("File found: ");
				sendMessage(chatId, "File not found: " + filename, telegramClient);
			} else {
				sendDocument(chatId, text.replace("/slik ", "").trim() + ".pdf", new InputFile(new ByteArrayInputStream(bytes), filename+".pdf"), telegramClient);
			}

		});
	}
	private Message sendNotification(Long chatId, String text, TelegramClient telegramClient) {
		try {
			return telegramClient.execute(SendMessage.builder()
				.chatId(chatId)
				.text(text)
				.parseMode("Markdown")
				.build());
		} catch (Exception e) {
			log.info(e.getMessage());
			return null;
		}
	}
	private void editMessage(Long chatId, Integer messageId, String text, TelegramClient telegramClient) {
		try {
			telegramClient.execute(EditMessageText.builder()
				.chatId(chatId)
				.text(text)
				.messageId(messageId)
				.parseMode("Markdown")
				.build());
		} catch (Exception e) {
			log.info(e.getMessage());
		}
	}
}
