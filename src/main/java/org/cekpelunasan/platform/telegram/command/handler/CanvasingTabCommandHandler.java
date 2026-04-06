package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationCanvassingByTab;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.utils.CanvasingUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler untuk perintah {@code /canvas} — mencari data tabungan nasabah berdasarkan alamat.
 *
 * <p>Berbeda dengan {@code /canvasing} yang menggunakan data riwayat kredit, perintah ini
 * mencari berdasarkan data tabungan aktif. Kata kunci alamat bisa dipisahkan dengan koma
 * atau spasi, dan pencarian akan mencocokkan semua kata kunci tersebut sekaligus.</p>
 *
 * <p>Contoh penggunaan: {@code /canvas Jl. Kenanga, Blok A} — bot akan mencari tabungan
 * dengan alamat yang mengandung semua kata kunci tersebut. Hasil ditampilkan dengan paginasi
 * 5 data per halaman menggunakan tombol inline.</p>
 *
 * <p>Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
@Component
@RequiredArgsConstructor
public class CanvasingTabCommandHandler extends AbstractCommandHandler {

	private final SavingsService savingsService;
	private final PaginationCanvassingByTab paginationCanvassingByTab;
	private final CanvasingUtils canvasingUtils;

	@Override
	public String getCommand() {
		return "/canvas";
	}

	@Override
	public String getDescription() {
		return "";
	}

	/**
	 * Memvalidasi peran pengguna sebelum memproses pencarian data tabungan.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return hasil pencarian tabungan, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Memecah kata kunci alamat dari input user dan mencari data tabungan yang cocok.
	 *
	 * <p>Teks setelah {@code /canvas } diambil sebagai query pencarian. Kata kunci bisa
	 * dipisahkan dengan koma maupun spasi biasa — keduanya akan diproses dengan cara yang sama.
	 * Hasil halaman pertama (5 data) langsung ditampilkan dengan tombol navigasi paginasi.</p>
	 *
	 * @param chatId ID chat pengguna yang mengirim perintah
	 * @param text   teks lengkap perintah termasuk kata kunci alamat
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah hasil dikirim ke user
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String address = text.length() > 8 ? text.substring(8).trim() : "";
		if (address.isEmpty()) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "Format salah, silahkan gunakan /canvas <alamat>", client));
		}
		List<String> addressList = Arrays.stream(address.split(","))
			.flatMap(part -> Arrays.stream(part.trim().split("\\s+")))
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());

		return savingsService.findFilteredSavings(addressList, PageRequest.of(0, 5))
			.flatMap(savingsPage -> Mono.fromRunnable(() -> {
				if (savingsPage.isEmpty()) {
					sendMessage(chatId, "Tidak ada data yang ditemukan", client);
					return;
				}
				StringBuilder message = new StringBuilder("📊 *INFORMASI TABUNGAN*\n")
					.append("───────────────────\n")
					.append("📄 Halaman 1 dari ").append(savingsPage.getTotalPages()).append("\n\n");
				savingsPage.forEach(dto -> message.append(canvasingUtils.canvasingTab(dto)));
				sendMessage(chatId, message.toString(), paginationCanvassingByTab.dynamicButtonName(savingsPage, 0, address), client);
			}))
			.then();
	}
}
