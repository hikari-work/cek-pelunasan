package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.button.ButtonListForSelectBranch;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /tgnama} — mencari tagihan berdasarkan nama nasabah.
 *
 * <p>Berguna ketika user tidak mengetahui nomor SPK tapi tahu nama nasabahnya.
 * Format penggunaan: {@code /tgnama <nama_nasabah>}, misalnya {@code /tgnama Budi}.</p>
 *
 * <p>Karena nama nasabah bisa muncul di lebih dari satu cabang, hasil pencarian
 * menampilkan daftar cabang yang memiliki nasabah dengan nama tersebut. User kemudian
 * memilih cabang yang sesuai melalui tombol inline untuk melihat tagihan secara detail.</p>
 *
 * <p>Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagihWithNameCommandHandler extends AbstractCommandHandler {

	private final BillService billService;

	@Override
	public String getCommand() {
		return "/tgnama";
	}

	@Override
	public String getDescription() {
		return "Mengembalikan list nama yang anda cari jika anda tidak mengetahui ID SPK";
	}

	/**
	 * Memvalidasi peran pengguna sebelum memproses pencarian tagihan berdasarkan nama.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return hasil pencarian, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Mencari semua cabang yang memiliki tagihan dengan nama nasabah yang cocok, lalu meminta user memilih cabang.
	 *
	 * <p>Jika tidak ada argumen nama yang disertakan, bot menampilkan format yang benar.
	 * Jika nama ditemukan di beberapa cabang, bot menampilkan daftar tombol pilihan cabang
	 * agar user bisa memilih cabang yang tepat sebelum melihat detail tagihan.</p>
	 *
	 * @param chatId ID chat pengguna yang mengirim perintah
	 * @param text   teks lengkap perintah yang berisi nama nasabah yang dicari
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah pilihan cabang ditampilkan ke user
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String[] parts = text.split(" ", 2);
		if (parts.length < 2) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "❌ *Format tidak valid*\n\nContoh: /tgnama 1234567890", client));
		}
		String name = parts[1].trim();
		return billService.lisAllBranch()
			.flatMap(branches -> Mono.fromRunnable(() -> {
				if (branches.isEmpty()) {
					sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
					return;
				}
				if (branches.size() > 1) {
					log.info("Data ditemukan dalam beberapa cabang: {}", branches);
					sendMessage(chatId,
						"⚠ *Terdapat lebih dari satu cabang dengan nama yang sama*\n\nSilakan pilih cabang yang sesuai:",
						new ButtonListForSelectBranch().dynamicSelectBranch(branches, name),
						client);
				}
			}))
			.then();
	}
}
