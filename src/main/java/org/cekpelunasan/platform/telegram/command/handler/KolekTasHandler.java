package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationKolekTas;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.kolektas.KolekTasService;
import org.cekpelunasan.utils.KolekTasUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /kolektas} — mencari data kolektibilitas tabungan berdasarkan kelompok.
 *
 * <p>Perintah ini digunakan untuk mengambil informasi kolektibilitas (kualitas kredit/tabungan)
 * berdasarkan nama kelompok nasabah. Format penggunaannya: {@code /kolektas <nama_kelompok>},
 * misalnya {@code /kolektas mawar}.</p>
 *
 * <p>Data yang ditemukan ditampilkan secara terformat menggunakan {@link KolekTasUtils},
 * dengan dukungan paginasi melalui tombol inline jika hasilnya lebih dari satu halaman.</p>
 *
 * <p>Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
@Component
@RequiredArgsConstructor
public class KolekTasHandler extends AbstractCommandHandler {

	private final KolekTasService kolekTasService;
	private final KolekTasUtils kolekTasUtils;
	private final PaginationKolekTas paginationKolekTas;

	@Override
	public String getCommand() {
		return "/kolektas";
	}

	@Override
	public String getDescription() {
		return "Kolek Tas";
	}

	/**
	 * Memvalidasi peran pengguna sebelum memproses pencarian data kolektibilitas.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return hasil pencarian, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.AO, AccountOfficerRoles.PIMP, AccountOfficerRoles.ADMIN})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Mencari data kolektibilitas berdasarkan nama kelompok yang diberikan user.
	 *
	 * <p>Nama kelompok diambil dari teks setelah {@code /kolektas }, dikonversi ke huruf kecil
	 * sebelum pencarian. Jika argumen kosong, bot meminta user mengisi nama kelompok.
	 * Hasil halaman pertama (5 data) ditampilkan beserta tombol navigasi paginasi.</p>
	 *
	 * @param chatId ID chat pengguna yang mengirim perintah
	 * @param text   teks lengkap perintah termasuk nama kelompok
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah hasil pencarian dikirim ke user
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String[] parts = text.split(" ", 2);
		if (parts.length < 2 || parts[1].trim().isEmpty()) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "Data Tidak Boleh Kosong", client));
		}
		String data = parts[1].trim().toLowerCase();
		return kolekTasService.findKolekByKelompok(data, 0, 5)
			.flatMap(kolek -> Mono.fromRunnable(() -> {
				StringBuilder sb = new StringBuilder();
				kolek.forEach(k -> sb.append(kolekTasUtils.buildKolekTas(k)));
				sendMessage(chatId, sb.toString(), paginationKolekTas.dynamicButtonName(kolek, 0, data), client);
			}))
			.then();
	}
}
