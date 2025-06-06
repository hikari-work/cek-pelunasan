package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.slik.GeneratePDF;
import org.cekpelunasan.service.slik.PDFReader;
import org.cekpelunasan.service.slik.S3Connector;
import org.cekpelunasan.service.users.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class SlikCommand implements CommandProcessor {


	private final S3Connector s3Connector;
	private final GeneratePDF generatePDF;
	private final AuthorizedChats authorizedChats1;
	private final MessageTemplate messageTemplate;
	private final UserService userService;
	private final PDFReader pDFReader;

	public SlikCommand(S3Connector s3Connector, GeneratePDF generatePDF, AuthorizedChats authorizedChats1, MessageTemplate messageTemplate, UserService userService1, PDFReader pDFReader) {
		this.s3Connector = s3Connector;
		this.generatePDF = generatePDF;
		this.authorizedChats1 = authorizedChats1;
		this.messageTemplate = messageTemplate;
		this.userService = userService1;
		this.pDFReader = pDFReader;
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
			if (!filename.matches("\\b\\d{16}\\b")) {
				String s = handleSlikByNameCommand(text.replace("/slik ", ""), chatId);
				if (s == null) {
					sendMessage(chatId, "SLIK Belum di request, Atau anda belum didaftarkan sebagai AO", telegramClient);
					return;
				}
				sendMessage(chatId, s, telegramClient);
				return;
			}
			Message message = sendNotification(chatId, telegramClient);
			byte[] files = s3Connector.getFile("KTP_" + filename + ".txt");
			if (files == null) {
				log.info("File found: ");
				editMessage(chatId, message.getMessageId(), "Data KTP `" + filename +"` tidak ada", telegramClient);
			}
			String s = generatePDF.sendBytesWithRestTemplate(files, filename + ".txt");
			if (s == null || s.isEmpty()) {
				log.info("File not found: {}", filename);
				editMessage(chatId, message.getMessageId(), "Data KTP `" + filename +"` tidak ada", telegramClient);
				return;
			}
			byte[] bytes = generatePDF.convertHtmlToPdf(s);
			editMessage(chatId, message.getMessageId(), "Data KTP `" + filename +"` Ditemukan....", telegramClient);
			if (files == null || bytes.length == 0) {
				log.info("File not found: ");
				sendMessage(chatId, "File not found: " + filename, telegramClient);
			} else {
				log.info("Sending Files....");
				sendDocument(chatId, text.replace("/slik ", "").trim() + ".pdf", new InputFile(new ByteArrayInputStream(bytes), filename+".pdf"), telegramClient);
			}

		});
	}
	private Message sendNotification(Long chatId, TelegramClient telegramClient) {
		try {
			return telegramClient.execute(SendMessage.builder()
				.chatId(chatId)
				.text("Mengambil Data KTP")
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
	public String handleSlikByNameCommand(String text, long chatId) {
    log.info("Melakukan pencarian {}", text);
    Optional<User> userByChatId = userService.findUserByChatId(chatId);
    if (userByChatId.isEmpty()) {
        return null;
    }
    User user = userByChatId.get();
    log.info("Pencarian dengan {}_{}", user.getUserCode(), text.trim());
    List<String> list = s3Connector.listObjectFoundByName(user.getUserCode() + "_" + text.trim());
    
    if (list.isEmpty()) {
        return String.format("""
            🔍 *HASIL PENCARIAN*
            ━━━━━━━━━━━━━━━━━━━
            
            ❌ *Tidak ditemukan hasil untuk "%s"*
            Silahkan periksa lagi, apakah SLIK sudah di request atau belum?
            atau kamu belum validasi AO? Kalau belum silahkan validasi dahulu...
            Caranya :
            /otor <3 Huruf Inisial>
            
            _Silakan coba dengan kata kunci lain atau periksa penulisan karena pencarian case sensitive_
            """, text);
    }
    
    StringBuilder builder = new StringBuilder(String.format("""
        🔍 *HASIL PENCARIAN*
        ━━━━━━━━━━━━━━━━━━━
        
        📝 Kata kunci: *%s*
        🔢 Ditemukan: *%d dokumen*
        👤 User: *%s*
        
        📋 *DAFTAR DOKUMEN*
        """, text, list.size(), user.getUserCode()));
    
    int counter = 1;
    for (String contentKey : list) {
		log.info("Dokumen {}", contentKey);
        String idNumber = pDFReader.generateIDNumber(s3Connector.getFile(contentKey));
        
        builder.append(String.format("""
            
            📄 *Dokumen #%d*
            ┌─────────────────────
            │ 📂 Nama: `%s`
            │ 🪪 No KTP: %s
            │ 🪪 Command Resume: %s
            │ 🪪 Original SLIK: %s
            └─────────────────────
            """, 
            counter++,
            contentKey,
            (idNumber != null ? "`" + idNumber + "`" : "_Tidak ditemukan_"),
            (idNumber != null ? "`/slik " + idNumber + "`" : "_Tidak ditemukan_"),
            (contentKey != null ? "`/doc " + contentKey + "`" : "_Tidak ditemukan_")
        ));
    }
    
    builder.append("""
        ℹ️ _Tap pada Command untuk menyalin dan mendapatkan File_
        ⏱️ _Generated:""").append(java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("_");
    
    return builder.toString();
}
}