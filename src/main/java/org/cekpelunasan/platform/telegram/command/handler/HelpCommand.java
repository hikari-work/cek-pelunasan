package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.button.HelpButton;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /help} — menampilkan panduan penggunaan bot.
 *
 * <p>Saat user mengirim {@code /help}, bot menampilkan pesan singkat tentang fungsi utama bot
 * beserta tombol-tombol bantuan interaktif yang dibuat oleh {@link HelpButton}.
 * Tombol tersebut memudahkan user mengakses fitur-fitur utama bot tanpa perlu
 * mengingat semua perintah satu per satu.</p>
 *
 * <p>Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
@Component
@RequiredArgsConstructor
public class HelpCommand extends AbstractCommandHandler {

	private final HelpButton helpButton;

	@Override
	public String getCommand() {
		return "/help";
	}

	@Override
	public String getDescription() {
		return "";
	}

	/**
	 * Memvalidasi peran pengguna sebelum menampilkan menu bantuan.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return menu bantuan, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Mengirimkan pesan bantuan beserta tombol navigasi fitur bot ke user.
	 *
	 * @param chatId ID chat pengguna yang meminta bantuan
	 * @param text   teks perintah (tidak digunakan)
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah pesan bantuan berhasil dikirim
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		return Mono.fromRunnable(() -> {
			String message = "Bot Ini Digunakan untuk mencari tagihan dan pelunasan\n";
			sendMessage(chatId, message, helpButton.sendHelpMessage(), client);
		});
	}
}
