package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /doc} — mengambil dan mengirimkan dokumen SLIK dari penyimpanan S3.
 *
 * <p>Perintah ini memungkinkan user mengunduh file dokumen yang tersimpan di S3
 * langsung ke chat Telegram mereka. Format penggunaannya: {@code /doc <nama_file>},
 * misalnya {@code /doc laporan_slik_2024.pdf}.</p>
 *
 * <p>Jika file tidak ditemukan di S3, bot akan memberitahu user dengan pesan yang jelas.
 * File yang berhasil ditemukan akan langsung dikirim sebagai dokumen ke chat user.</p>
 *
 * <p>Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
@Component
@RequiredArgsConstructor
public class DocSlikCommandHandler extends AbstractCommandHandler {

	private final S3ClientConfiguration s3Connector;

	@Override
	public String getCommand() {
		return "/doc";
	}

	@Override
	public String getDescription() {
		return "";
	}

	/**
	 * Memvalidasi peran pengguna sebelum memproses pengambilan dokumen.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return file dokumen yang dikirim ke chat, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Mengambil file dari S3 berdasarkan nama yang diberikan dan mengirimkannya ke chat user.
	 *
	 * <p>Nama file diambil dari teks setelah {@code /doc }. Jika nama kosong atau
	 * perintah dikirim tanpa argumen, bot meminta user untuk menyertakan nama file.
	 * Jika file tidak ada di S3, bot memberitahu bahwa file tidak ditemukan.</p>
	 *
	 * @param chatId ID chat pengguna yang meminta dokumen
	 * @param text   teks lengkap perintah yang berisi nama file
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah dokumen berhasil dikirim atau error ditangani
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String name = text.replace("/doc ", "").trim();
		if (name.isEmpty() || name.equals("/doc")) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "Nama Harus Diisi", client));
		}
		return s3Connector.getFile(name)
			.switchIfEmpty(Mono.fromRunnable(() -> sendMessage(chatId, "File tidak ditemukan", client)))
			.flatMap(file -> Mono.fromRunnable(() -> sendDocument(chatId, name, file, client)))
			.then();
	}
}
