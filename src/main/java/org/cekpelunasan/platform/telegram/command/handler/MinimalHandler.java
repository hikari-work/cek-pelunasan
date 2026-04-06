package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.simulasi.SimulasiService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /minimal} — menghitung jumlah minimal bayar untuk satu kredit.
 *
 * <p>Berdasarkan nomor SPK yang diberikan, bot menghitung berapa minimal pembayaran
 * yang harus dilakukan agar status kredit tetap aman. Fitur ini masih dalam tahap BETA,
 * sehingga user diminta melaporkan jika ada ketidaksesuaian perhitungan.</p>
 *
 * <p>Format penggunaan: {@code /minimal <no_spk>} dengan nomor SPK 12 digit,
 * misalnya {@code /minimal 123456789012}.</p>
 *
 * <p>Jika jumlah minimal bayar adalah 0 atau negatif, artinya angsuran masih aman
 * sampai akhir bulan dan bot akan memberitahu hal tersebut.</p>
 *
 * <p>Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
@Component
@RequiredArgsConstructor
public class MinimalHandler extends AbstractCommandHandler {

	private final SimulasiService simulasiService;

	@Override
	public String getCommand() {
		return "/minimal";
	}

	@Override
	public String getDescription() {
		return "";
	}

	/**
	 * Memvalidasi peran pengguna sebelum menghitung minimal bayar.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return hasil perhitungan minimal bayar, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Menghitung dan menampilkan jumlah minimal bayar untuk kredit dengan nomor SPK tertentu.
	 *
	 * <p>Nomor SPK divalidasi terlebih dahulu — harus tepat 12 digit angka. Jika tidak sesuai,
	 * bot memberikan contoh format yang benar. Hasil perhitungan ditampilkan dalam format
	 * rupiah yang mudah dibaca, beserta peringatan bahwa fitur ini masih BETA.</p>
	 *
	 * @param chatId ID chat pengguna yang mengirim perintah
	 * @param text   teks perintah yang berisi nomor SPK
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah hasil perhitungan dikirim ke user
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String noSpk = text.replace("/minimal ", "");
		if (!noSpk.matches("\\d{12}")) {
			return Mono.fromRunnable(() ->
				sendMessage(chatId, "❌ Nomor SPK harus berupa 12 digit angka.\nContoh yang benar: 123456789012", client));
		}
		return simulasiService.minimalBayar(noSpk)
			.flatMap(minimalBayar -> Mono.fromRunnable(() -> {
				if (minimalBayar > 0) {
					sendMessage(chatId, String.format("""
						📊 *Hasil Minimal Masuk Angsuran*

						_Ini adalah fitur BETA_
						_Laporkan jika ada kesalahan perhitungan_

						🧾 No SPK: `%s`
						💰 Minimal bayar: *Rp%s*
						""",
						noSpk,
						String.format("%,d", minimalBayar).replace(',', '.')
					), client);
				} else {
					sendMessage(chatId, "Angsuran Aman Sampai Akhir Bulan", client);
				}
			}))
			.then();
	}
}
