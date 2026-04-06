package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.MessageTemplate;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /deauth} — mencabut akses bot dari user tertentu.
 *
 * <p>Kebalikan dari {@code /auth}, perintah ini digunakan admin untuk mencabut izin akses
 * seorang user. Format perintahnya: {@code /deauth <chat_id>}. Setelah berhasil, user yang
 * bersangkutan mendapat notifikasi bahwa akses mereka sudah dicabut, dan owner bot mendapat
 * konfirmasi bahwa proses berhasil.</p>
 *
 * <p>Data user dihapus dari database melalui {@link UserService} sekaligus dari cache
 * in-memory {@link AuthorizedChats}, sehingga user tersebut langsung tidak bisa
 * menggunakan perintah bot yang memerlukan otorisasi.</p>
 *
 * <p>Hanya admin yang dapat menjalankan perintah ini.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteUserAccessCommand extends AbstractCommandHandler {

	private final AuthorizedChats authorizedChats;
	private final UserService userService;
	private final MessageTemplate messageTemplate;

	@Value("${telegram.bot.owner}")
	private Long ownerId;

	@Override
	public String getCommand() {
		return "/deauth";
	}

	@Override
	public String getDescription() {
		return "Gunakan Command ini untuk menghapus izin user.";
	}

	/**
	 * Memvalidasi bahwa pengirim adalah admin sebelum memproses pencabutan akses.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return hasil proses deauth, atau ditolak jika bukan admin
	 */
	@Override
	@RequireAuth(roles = AccountOfficerRoles.ADMIN)
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Mencabut akses bot dari user berdasarkan {@code chat_id} yang diberikan.
	 *
	 * <p>Format perintah yang valid adalah {@code /deauth <chat_id>} dengan {@code chat_id} berupa angka.
	 * Jika format tidak sesuai atau bukan angka, bot membalas dengan pesan error yang informatif.
	 * Setelah berhasil, user target mendapat pesan bahwa aksesnya dicabut, dan owner mendapat
	 * konfirmasi singkat.</p>
	 *
	 * @param chatId ID chat admin yang mengirim perintah
	 * @param text   teks lengkap perintah yang berisi {@code chat_id} target
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah proses pencabutan akses berhasil atau gagal dengan error
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String[] parts = text.split(" ");
		if (parts.length < 2) {
			return Mono.fromRunnable(() -> sendMessage(chatId, messageTemplate.notValidDeauthFormat(), client));
		}
		long target;
		try {
			target = Long.parseLong(parts[1]);
		} catch (NumberFormatException e) {
			return Mono.fromRunnable(() -> sendMessage(chatId, messageTemplate.notValidNumber(), client));
		}
		log.info("{} Sudah ditendang", target);
		return userService.deleteUser(target)
			.doOnSuccess(v -> {
				authorizedChats.deleteUser(target);
				sendMessage(target, messageTemplate.unathorizedMessage(), client);
				sendMessage(ownerId, "Sukses", client);
			});
	}
}
