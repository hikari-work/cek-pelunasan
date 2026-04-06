package org.cekpelunasan.platform.whatsapp.service.shortcut;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.platform.whatsapp.service.sender.WhatsAppSenderService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mengelola perintah shortcut admin yang diawali dengan slash (/).
 * <p>
 * Fitur ini memudahkan admin CS untuk membalas pertanyaan umum dengan cepat
 * tanpa harus mengetik ulang. Cukup ketik perintah seperti "/coba" atau "/tunggu",
 * dan bot langsung mengedit pesan tersebut menjadi balasan yang sudah terdaftar.
 * </p>
 * <p>
 * Daftar shortcut yang tersedia:
 * <ul>
 *   <li>{@code /coba} — ajak pengguna mencoba lagi</li>
 *   <li>{@code /kasih} — ucapkan terima kasih kembali</li>
 *   <li>{@code /tunggu} — minta pengguna menunggu</li>
 *   <li>{@code /relog} — minta pengguna relogin</li>
 *   <li>{@code /selesai} — minta notifikasi jika sudah selesai</li>
 *   <li>{@code /enter} — minta enter lagi dan notifikasi jika selesai</li>
 *   <li>{@code /input} — konfirmasi sudah diinput</li>
 *   <li>{@code /display} — konfirmasi display sudah dibebaskan</li>
 *   <li>{@code /terima} — ucapkan terima kasih</li>
 * </ul>
 * </p>
 */
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

	/**
	 * Memproses perintah shortcut dan mengedit pesan asli dengan balasan yang sesuai.
	 * <p>
	 * Kalau shortcut dikenali, pesan admin di-edit otomatis menjadi teks balasan
	 * yang sudah terdaftar. Kalau tidak dikenali, cukup dicatat ke log sebagai warning.
	 * </p>
	 *
	 * @param message data webhook dari perintah shortcut yang dikirim admin
	 * @return CompletableFuture yang selesai setelah proses pengiriman dimulai
	 */
	@SuppressWarnings("UnusedReturnValue")
	public CompletableFuture<Void> sendShortcutMessage(WhatsAppWebhookDTO message) {
		log.info("Goto ShortCutMessage");
		String text = message.getPayload().getBody();
		String response = shortcutResponses.get(text);

		if (response != null) {
			whatsAppSenderService.updateMessage(
				message.buildChatId(),
				message.getPayload().getId(),
				response
			).subscribe();
			return CompletableFuture.completedFuture(null);
		}

		log.warn("Unknown shortcut command: {}", text);
		return CompletableFuture.completedFuture(null);
	}
}