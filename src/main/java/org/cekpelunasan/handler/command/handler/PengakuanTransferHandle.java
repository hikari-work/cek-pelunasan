package org.cekpelunasan.handler.command.handler;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.service.gemini.PengakuanGeminiService;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PengakuanTransferHandle {

	private static final Logger log = LoggerFactory.getLogger(PengakuanTransferHandle.class);
	private final ObjectMapper mapper;

	private final PengakuanGeminiService pengakuanGeminiService;
	private final RupiahFormatUtils rupiahFormatUtils;
	@Value("${telegram.bot.token}")
	private String telegramBotToken;


	public void handle(Update update, TelegramClient telegramClient) {
		try {
			long chatId = update.getMessage().getChatId();

			List<PhotoSize> photos = update.getMessage().getPhoto();
			PhotoSize photoSize = photos.stream()
				.max(Comparator.comparing(PhotoSize::getFileSize))
				.orElseThrow(() -> new IllegalStateException("No photo found"));

			String fileId = photoSize.getFileId();
			String imageBase64 = downloadAndEncodeImage(fileId, telegramClient);
			String textPromt = """
				Tolong analisakan bukti transafer ini, kemblalikan dalam bentuk valid JSON,
				dengan berisikan object nominal, tanggal transfer, dan tujuan transfer, dan keterangan transfer. Hati hati dengan biaya kirim nya, jangan sampai memasukkan biaya kirim
				Contoh kebalian datanya adalah seperti ini
				
				{
					"nominal": 100000,
					"tanggal" : "26 Juni 2025",
					"tujuan" : "1234567890",
					"keterangan" : "Transfer ke rekening 1234567890"
				}
				Paring data tanggal menjadi DD MMMM YYYY, contoh 26 Juni 2025
				tujuan transfer hanya boleh nilai, tidak ada penghubung seperti . atau -
				PENTING : Kembalikan hanya raw JSON dengan field diatas, jangan kirimkan kirimkan code block formatting, markdown sytax atau text lainnya.
				""";
			String geminiResponse = pengakuanGeminiService.askGemini(textPromt, imageBase64).replace("```json", "").replace("```", "");
			log.info("Response from Gemini: {}", geminiResponse);
			try {
				PengakuanTransfer pengakuanTransfer = mapper.readValue(geminiResponse, PengakuanTransfer.class);
				sendMessage(chatId, extractPengakuanTranfer(pengakuanTransfer), telegramClient);
			} catch (JsonParseException e) {
				sendMessage(chatId, geminiResponse, telegramClient);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	private String downloadAndEncodeImage(String fileId, TelegramClient telegramClient) throws TelegramApiException {
		GetFile getFile = GetFile.builder()
			.fileId(fileId)
			.build();
		File file = telegramClient.execute(getFile);

		String filePath = file.getFilePath();
		String fullFilePath = "https://api.telegram.org/file/bot" + telegramBotToken + "/" + filePath;
		URL url;
		try {
			url = new URL(fullFilePath);
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
		try (InputStream inputStream = url.openStream()){
			byte[] imageBytes = inputStream.readAllBytes();
			return Base64.getEncoder().encodeToString(imageBytes);
		} catch (Exception e) {
			throw new IllegalStateException("Gagal mengambil gambar", e);
		}
	}
	private void sendMessage(long chatId, String message, TelegramClient telegramClient) {
		try {
			telegramClient.execute(SendMessage.builder()
				.chatId(chatId)
				.text(message).parseMode("Markdown")
				.build());
		} catch (TelegramApiException e) {
			throw new RuntimeException(e);
		}
	}

	@Data
	public static class PengakuanTransfer {
		private Long nominal;
		private String tanggal;
		private String tujuan;
		private String keterangan;
	}

	private String extractPengakuanTranfer(PengakuanTransfer transfer) {

    	return String.format("""
			✅ *TRANSFER BERHASIL TERVERIFIKASI*
			
			💰 *Nominal*: %s
			🗓️ *Tanggal*: %s
			🏦 *Tujuan*: %s
			📝 *Keterangan*: %s
			
			_Terima kasih telah menggunakan layanan kami._
			_Simpan bukti ini sebagai referensi transaksi Anda._
			""", rupiahFormatUtils.formatRupiah(transfer.getNominal()),
        	transfer.getTanggal(),
        	transfer.getTujuan(),
        	transfer.getKeterangan()
    	);
	}
}