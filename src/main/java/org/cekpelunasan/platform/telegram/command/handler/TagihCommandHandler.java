package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.MessageTemplate;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.TagihanUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /tagih} — menampilkan rincian lengkap satu tagihan berdasarkan nomor SPK.
 *
 * <p>Perintah ini mengambil dan menampilkan detail tagihan untuk satu nasabah berdasarkan
 * nomor SPK yang diberikan. Format penggunaan: {@code /tagih <no_spk>},
 * misalnya {@code /tagih 123456789012}.</p>
 *
 * <p>Rincian yang ditampilkan mencakup semua informasi tagihan yang tersedia, diformat
 * dengan rapi oleh {@link TagihanUtils}. Waktu eksekusi query juga ditampilkan
 * di akhir pesan untuk transparansi performa sistem.</p>
 *
 * <p>Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagihCommandHandler extends AbstractCommandHandler {

	private final BillService billService;
	private final MessageTemplate messageTemplate;
	private final TagihanUtils tagihanUtils;

	@Override
	public String getCommand() {
		return "/tagih";
	}

	@Override
	public String getDescription() {
		return "Mengembalikan rincian tagihan berdasarkan ID SPK yang anda kirimkan";
	}

	/**
	 * Memvalidasi peran pengguna sebelum mengambil detail tagihan.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return detail tagihan yang diminta, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.AO, AccountOfficerRoles.ADMIN, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Mencari tagihan berdasarkan nomor SPK dan menampilkan rinciannya ke user.
	 *
	 * <p>Nomor SPK diambil dari bagian kedua teks perintah. Jika tidak ada argumen yang disertakan,
	 * bot menampilkan petunjuk format yang benar. Jika nomor SPK tidak ditemukan di database,
	 * bot memberitahu bahwa data tidak ada. Waktu eksekusi pencarian selalu disertakan di pesan.</p>
	 *
	 * @param chatId ID chat pengguna yang mengirim perintah
	 * @param text   teks lengkap perintah yang berisi nomor SPK
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah rincian tagihan berhasil dikirim atau error ditangani
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String[] parts = text.split(" ", 2);
		if (parts.length < 2) {
			return Mono.fromRunnable(() -> sendMessage(chatId, messageTemplate.notValidDeauthFormat(), client));
		}
		long start = System.currentTimeMillis();
		return billService.getBillById(parts[1])
			.switchIfEmpty(Mono.fromRunnable(() -> sendMessage(chatId, "❌ *Data tidak ditemukan*", client)))
			.flatMap(bills -> Mono.fromRunnable(() ->
				sendMessage(chatId, tagihanUtils.detailBills(bills) + "\nEksekusi dalam " + (System.currentTimeMillis() - start) + " ms", client)))
			.onErrorResume(e -> {
				log.error("Error", e);
				return Mono.empty();
			})
			.then();
	}
}
