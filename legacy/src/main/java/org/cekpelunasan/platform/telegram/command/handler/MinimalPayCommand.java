package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationToMinimalPay;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.MinimalPayUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /pabpr} — menampilkan daftar tagihan yang masih memiliki sisa minimal bayar.
 *
 * <p>Perintah ini membantu AO dan pimpinan mengidentifikasi kredit mana saja yang
 * masih memiliki kewajiban minimal bayar yang belum lunas. Data yang ditampilkan
 * disesuaikan dengan peran pengguna: AO melihat tagihan miliknya, sedangkan
 * pimpinan dan admin melihat tagihan berdasarkan kode cabang mereka.</p>
 *
 * <p>Hasil ditampilkan dengan paginasi 5 data per halaman menggunakan tombol inline,
 * beserta catatan pengingat agar pembayaran dilakukan sebelum tanggal jatuh bayar.</p>
 *
 * <p>Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
@Component
@RequiredArgsConstructor
public class MinimalPayCommand extends AbstractCommandHandler {

	private final UserService userService;
	private final BillService billService;
	private final PaginationToMinimalPay paginationToMinimalPay;
	private final MinimalPayUtils minimalPayUtils;

	@Override
	public String getCommand() {
		return "/pabpr";
	}

	@Override
	public String getDescription() {
		return "Menampilkan daftar tagihan yang masih memiliki minimal bayar tersisa.";
	}

	/**
	 * Memvalidasi peran pengguna sebelum mengambil daftar tagihan minimal bayar.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return daftar tagihan minimal bayar, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.AO, AccountOfficerRoles.PIMP, AccountOfficerRoles.ADMIN})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Mengambil dan menampilkan daftar tagihan dengan sisa minimal bayar berdasarkan peran user.
	 *
	 * <p>Data yang diambil berbeda tergantung peran:</p>
	 * <ul>
	 *   <li>AO: tagihan dengan minimal bayar tersisa yang menjadi tanggung jawabnya.</li>
	 *   <li>Pimpinan dan Admin: tagihan minimal bayar seluruh cabang berdasarkan kode cabang.</li>
	 * </ul>
	 * <p>Jika tidak ada tagihan yang ditemukan, bot memberitahu user bahwa semua tagihan sudah bersih.</p>
	 *
	 * @param chatId ID chat pengguna yang mengirim perintah
	 * @param text   teks perintah (tidak digunakan)
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah daftar tagihan dikirim ke user
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		return userService.findUserByChatId(chatId)
			.switchIfEmpty(Mono.fromRunnable(() -> sendMessage(chatId, "❌ *User tidak ditemukan*", client)))
			.flatMap(user -> {
				String userCode = user.getUserCode();
				if (user.getRoles() == null) {
					return Mono.empty();
				}
				Mono<Page<Bills>> billsMono = switch (user.getRoles()) {
					case AO -> billService.findMinimalPaymentByAccountOfficer(userCode, 0, 5);
					case PIMP, ADMIN -> billService.findMinimalPaymentByBranch(userCode, 0, 5);
				};
				return billsMono.flatMap(bills -> Mono.fromRunnable(() -> {
					if (bills.isEmpty()) {
						sendMessage(chatId, "❌ *Tidak ada tagihan dengan minimal bayar tersisa.*", client);
						return;
					}
					StringBuilder message = new StringBuilder("""
						📋 *DAFTAR TAGIHAN MINIMAL*
						═══════════════════════════

						""");
					bills.forEach(bill -> message.append(minimalPayUtils.minimalPay(bill)));
					message.append("""
						⚠️ *Catatan Penting*:
						▢ _Tap SPK untuk menyalin_
						▢ _Pembayaran harus dilakukan sebelum jatuh bayar_
						""");
					sendMessage(chatId, message.toString(), paginationToMinimalPay.dynamicButtonName(bills, 0, userCode), client);
				}));
			})
			.then();
	}
}
