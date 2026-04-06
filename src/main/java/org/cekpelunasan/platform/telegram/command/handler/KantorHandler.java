package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /kantor} — melihat atau mengubah kode cabang yang terdaftar untuk user.
 *
 * <p>Perintah ini memiliki dua mode penggunaan:</p>
 * <ul>
 *   <li>{@code /kantor} (tanpa argumen): menampilkan kode cabang yang saat ini terdaftar untuk user.</li>
 *   <li>{@code /kantor <kode>}: mengubah kode cabang user ke kode 4 digit yang diberikan,
 *       misalnya {@code /kantor 0101}.</li>
 * </ul>
 *
 * <p>Kode cabang ini penting karena digunakan untuk memfilter data yang ditampilkan
 * ke user, khususnya untuk pimpinan cabang yang hanya boleh melihat data dari kantornya sendiri.
 * Kode kantor harus tepat 4 karakter, tidak lebih dan tidak kurang.</p>
 *
 * <p>Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
@Component
@RequiredArgsConstructor
public class KantorHandler extends AbstractCommandHandler {

	private final UserService userService;

	@Override
	public String getCommand() {
		return "/kantor";
	}

	@Override
	public String getDescription() {
		return "";
	}

	/**
	 * Memvalidasi peran pengguna sebelum memproses permintaan kantor.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return hasil proses, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.PIMP, AccountOfficerRoles.AO, AccountOfficerRoles.ADMIN})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Menampilkan kode kantor saat ini atau menyimpan kode kantor baru berdasarkan input user.
	 *
	 * <p>Jika perintah dikirim tanpa argumen, bot menampilkan kode kantor yang sudah terdaftar
	 * untuk user tersebut. Jika user belum terdaftar di kantor manapun, bot memberitahu hal tersebut.
	 * Jika ada argumen, bot memvalidasi bahwa kode tersebut tepat 4 karakter sebelum menyimpannya.</p>
	 *
	 * @param chatId ID chat pengguna yang mengirim perintah
	 * @param text   teks perintah, bisa {@code /kantor} atau {@code /kantor <kode>}
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah informasi kantor ditampilkan atau diperbarui
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		if (text.equals("/kantor")) {
			return userService.findUserBranch(chatId)
				.switchIfEmpty(Mono.fromRunnable(() -> sendMessage(chatId, "Anda Tidak terdaftar di kantor manapun", client)))
				.flatMap(userBranch -> Mono.fromRunnable(() ->
					sendMessage(chatId, String.format("Anda sebelumnya terdaftar di kantor %s", userBranch), client)))
				.then();
		}
		String kantor = text.replace("/kantor ", "").trim();
		if (kantor.length() != 4) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "Format Kantor Tidak tepat!!!", client));
		}
		return userService.saveUserBranch(chatId, kantor)
			.doOnSuccess(v -> sendMessage(chatId, String.format("Sukses mengubah kantor anda menjadi %s", kantor), client));
	}
}
