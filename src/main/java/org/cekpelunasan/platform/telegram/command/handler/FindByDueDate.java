package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationBillsByNameCallbackHandler;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.DateUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Handler untuk perintah {@code /jb} — menampilkan daftar tagihan yang jatuh tempo hari ini.
 *
 * <p>Perintah ini memudahkan AO dan pimpinan untuk melihat semua tagihan yang harus
 * ditagih pada hari yang sama. Data yang ditampilkan disesuaikan dengan peran pengguna:
 * AO hanya melihat tagihan miliknya sendiri, sementara pimpinan melihat tagihan
 * seluruh cabang yang mereka pimpin.</p>
 *
 * <p>Hasil ditampilkan dengan paginasi 5 data per halaman. Setiap tagihan menampilkan
 * nama nasabah, nomor SPK, alamat, tanggal jatuh tempo, jumlah tagihan, dan nama AO.</p>
 */
@Component
@RequiredArgsConstructor
public class FindByDueDate extends AbstractCommandHandler {

	private final UserService userService;
	private final BillService billService;
	private final DateUtils dateUtils;

	@Override
	public String getCommand() {
		return "/jb";
	}

	@Override
	public String getDescription() {
		return "📅 *Cek tagihan jatuh tempo hari ini*.\nGunakan command ini untuk melihat daftar tagihan Anda yang jatuh tempo hari ini.";
	}

	/**
	 * Memvalidasi peran pengguna sebelum mengambil data tagihan jatuh tempo.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return daftar tagihan jatuh tempo, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Mengambil dan menampilkan daftar tagihan yang jatuh tempo pada tanggal hari ini.
	 *
	 * <p>Tagihan yang ditampilkan disesuaikan dengan peran pengguna yang login:</p>
	 * <ul>
	 *   <li>AO: hanya tagihan yang menjadi tanggung jawabnya berdasarkan kode AO.</li>
	 *   <li>Pimpinan: tagihan seluruh cabang yang dipimpinnya beserta detail pay down.</li>
	 *   <li>Peran lain: tidak menampilkan data apapun.</li>
	 * </ul>
	 *
	 * @param chatId ID chat pengguna yang mengirim perintah
	 * @param text   teks perintah (tidak digunakan, tanggal selalu hari ini)
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah daftar tagihan dikirim ke user
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String today = dateUtils.converterDate(LocalDateTime.now(ZoneOffset.ofHours(7)));
		return userService.findUserByChatId(chatId)
			.switchIfEmpty(Mono.fromRunnable(() -> sendMessage(chatId, "❌ *User tidak ditemukan*", client)))
			.flatMap(user -> {
				String userCode = user.getUserCode();
				if (user.getRoles() == null) {
					return Mono.empty();
				}
				Mono<Page<Bills>> billsMono = switch (user.getRoles()) {
					case AO -> billService.findDueDateByAccountOfficer(userCode, today, 0, 5);
					case PIMP -> billService.findBranchAndPayDown(userCode, today, 0, 5);
					default -> Mono.just(Page.empty());
				};
				return billsMono.flatMap(billsPage -> Mono.fromRunnable(() -> {
					if (billsPage.isEmpty()) {
						sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
						return;
					}
					StringBuilder builder = new StringBuilder("Halaman 1 dari " + billsPage.getTotalPages() + "\n📋 *Daftar Tagihan Jatuh Tempo Hari Ini:*\n\n");
					billsPage.forEach(bills -> builder.append(messageBuilder(bills)));
					sendMessage(chatId, builder.toString(), new PaginationBillsByNameCallbackHandler().dynamicButtonName(billsPage, 0, userCode), client);
				}));
			})
			.then();
	}

	/**
	 * Membangun blok teks terformat untuk satu entri tagihan.
	 *
	 * <p>Format yang dihasilkan menampilkan nama nasabah, nomor SPK (yang bisa di-tap untuk disalin),
	 * alamat (dipotong jika lebih dari 30 karakter), tanggal jatuh tempo, jumlah tagihan,
	 * dan nama AO yang bertanggung jawab.</p>
	 *
	 * @param bills data satu tagihan yang akan diformat
	 * @return string terformat siap kirim ke Telegram dengan Markdown
	 */
	private String messageBuilder(Bills bills) {
		return String.format("""
				👤 *%s*
				┌──────────────────────
				│ 📎 *INFORMASI KREDIT*
				│ ├─ 🔖 SPK      : `%s`
				│ ├─ 📍 Alamat   : %s
				│ └─ 📅 Jth Tempo: %s
				│
				│ 💰 *RINCIAN*
				│ ├─ 💸 Tagihan  : Rp %,d
				│ └─ 👨‍💼 AO       : %s
				└──────────────────────

				ℹ️ _Tap SPK untuk menyalin_
				""",
			bills.getName(),
			bills.getNoSpk(),
			bills.getAddress().length() > 30 ? bills.getAddress().substring(0, 27) + "..." : bills.getAddress(),
			bills.getPayDown() != null ? bills.getPayDown() : "Tidak tersedia",
			bills.getFullPayment(),
			bills.getAccountOfficer()
		);
	}
}
