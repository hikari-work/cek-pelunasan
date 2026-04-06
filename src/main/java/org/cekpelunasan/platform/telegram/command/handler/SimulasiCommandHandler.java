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
 * Handler untuk perintah {@code /simulasi} — mensimulasikan pembayaran angsuran kredit.
 *
 * <p>Dengan perintah ini, AO atau pimpinan bisa menghitung berapa pokok dan bunga yang
 * akan masuk jika nasabah membayar sejumlah nominal tertentu untuk kredit dengan nomor SPK
 * yang diberikan. Berguna untuk perencanaan penagihan dan komunikasi dengan nasabah.</p>
 *
 * <p>Format penggunaan: {@code /simulasi <no_spk> <nominal>},
 * misalnya {@code /simulasi 123456789012 5000000}.</p>
 *
 * <p>Hasil simulasi menampilkan berapa pokok yang masuk, berapa bunga yang masuk,
 * dan sampai tanggal berapa keterlambatan bisa ditutupi dengan nominal tersebut.
 * Fitur ini masih BETA — user diminta melaporkan jika ada ketidaksesuaian perhitungan.</p>
 *
 * <p>Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
@Component
@RequiredArgsConstructor
public class SimulasiCommandHandler extends AbstractCommandHandler {

	private final SimulasiService simulasiService;

	@Override
	public String getCommand() {
		return "/simulasi";
	}

	@Override
	public String getDescription() {
		return "Melakukan simulasi pelunasan dengan format: /simulasi <No SPK> <Nominal>";
	}

	/**
	 * Memvalidasi peran pengguna sebelum menjalankan simulasi pembayaran.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return hasil simulasi, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Memvalidasi input dan menjalankan simulasi pembayaran angsuran kredit.
	 *
	 * <p>Input divalidasi secara bertahap:</p>
	 * <ol>
	 *   <li>Pastikan ada minimal 3 bagian teks (perintah, nomor SPK, nominal).</li>
	 *   <li>Nomor SPK harus tepat 12 digit angka.</li>
	 *   <li>Nominal harus dapat diurai sebagai bilangan bulat.</li>
	 * </ol>
	 * <p>Jika semua valid, simulasi dijalankan dan hasilnya ditampilkan dalam format yang mudah dibaca.</p>
	 *
	 * @param chatId ID chat pengguna yang mengirim perintah
	 * @param text   teks lengkap perintah termasuk nomor SPK dan nominal
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah hasil simulasi dikirim ke user
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String[] data = text.trim().split("\\s+");
		if (data.length < 3) {
			return Mono.fromRunnable(() ->
				sendMessage(chatId, "❌ Format salah!\nGunakan format seperti ini:\n/simulasi <No SPK> <Nominal>\nContoh: /simulasi 123456789012 5000000", client));
		}

		String noSpk = data[1];
		String nominalStr = data[2];

		if (!noSpk.matches("\\d{12}")) {
			return Mono.fromRunnable(() ->
				sendMessage(chatId, "❌ Nomor SPK harus berupa 12 digit angka.\nContoh yang benar: 123456789012", client));
		}

		long nominal;
		try {
			nominal = Long.parseLong(nominalStr);
		} catch (NumberFormatException e) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "❌ Nominal harus berupa angka. Contoh: 5000000", client));
		}

		return simulasiService.getSimulasi(noSpk, nominal)
			.flatMap(simulasi -> Mono.fromRunnable(() -> {
				String response = String.format(
					"""
						📊 *Hasil Simulasi Masuk Angsuran*

						_Ini adalah fitur BETA_
						_Laporkan jika ada kesalahan perhitungan_

						🧾 No SPK: `%s`
						💰 Masuk Pokok: *Rp%s*
						🏦 Masuk Bunga: *Rp%s*
						⏳ Keterlambatan hingga: *%s*""",
					noSpk,
					formatCurrency(simulasi.getMasukP()),
					formatCurrency(simulasi.getMasukI()),
					simulasi.getMaxDate()
				);
				sendMessage(chatId, response, client);
			}))
			.then();
	}

	/**
	 * Memformat angka menjadi representasi mata uang rupiah yang mudah dibaca.
	 *
	 * <p>Menggunakan titik sebagai pemisah ribuan, misalnya {@code 1500000} menjadi {@code 1.500.000}.</p>
	 *
	 * @param amount jumlah dalam satuan rupiah
	 * @return string jumlah yang sudah diformat dengan pemisah ribuan
	 */
	private String formatCurrency(long amount) {
		return String.format("%,d", amount).replace(',', '.');
	}
}
