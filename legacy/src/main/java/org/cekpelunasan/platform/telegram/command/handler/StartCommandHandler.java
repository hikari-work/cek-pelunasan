package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.MessageTemplate;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /start} — mengecek apakah bot sedang aktif dan merespons.
 *
 * <p>Perintah ini berfungsi seperti ping sederhana: jika bot aktif, user akan mendapat
 * balasan "PONG!!!". Namun respons yang ditampilkan juga bergantung pada status otorisasi user:</p>
 * <ul>
 *   <li>User yang sudah terotorisasi: mendapat balasan sapaan singkat.</li>
 *   <li>User yang belum terotorisasi: mendapat pesan bahwa mereka belum diizinkan menggunakan bot.</li>
 * </ul>
 *
 * <p>Tidak ada pembatasan peran untuk perintah ini — siapapun bisa mengirim {@code /start},
 * termasuk user yang belum terdaftar.</p>
 */
@Component
@RequiredArgsConstructor
public class StartCommandHandler extends AbstractCommandHandler {

	private static final String START_MESSAGE = "👋 *PONG!!!*\n";

	private final AuthorizedChats authService;
	private final MessageTemplate messageTemplate;

	@Override
	public String getCommand() {
		return "/start";
	}

	@Override
	public String getDescription() {
		return "Mengecek Bot Apakah Aktif";
	}

	/**
	 * Merespons perintah {@code /start} dengan pesan yang berbeda tergantung status otorisasi user.
	 *
	 * <p>User yang sudah terotorisasi mendapat balasan "PONG!!!" sebagai tanda bot aktif.
	 * User yang belum terotorisasi mendapat pesan yang menjelaskan bahwa mereka belum bisa menggunakan bot
	 * dan perlu meminta admin untuk memberikan akses.</p>
	 *
	 * @param update objek update lengkap dari Telegram yang berisi informasi pengirim
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah pesan respons terkirim
	 */
	@Override
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return Mono.fromRunnable(() -> {
			long chatId = update.message.chatId;
			String messageText = authService.isAuthorized(chatId) ? START_MESSAGE : messageTemplate.unathorizedMessage();
			sendMessage(chatId, messageText, client);
		});
	}
}
