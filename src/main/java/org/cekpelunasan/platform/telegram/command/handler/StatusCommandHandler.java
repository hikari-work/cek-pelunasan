package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.credithistory.CreditHistoryService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.SystemUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /status} — menampilkan ringkasan kondisi sistem dan statistik data.
 *
 * <p>Perintah ini memberikan gambaran cepat tentang kesehatan sistem, termasuk jumlah user terdaftar,
 * total data kredit, total tagihan aktif, beban sistem saat ini, serta waktu eksekusi query.
 * Semua data diambil secara paralel untuk meminimalkan waktu respons.</p>
 *
 * <p>Berguna untuk memantau kondisi sistem sehari-hari atau memeriksa apakah data sudah
 * ter-update dengan benar setelah proses upload CSV.</p>
 *
 * <p>Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatusCommandHandler extends AbstractCommandHandler {

	private final UserService userService;
	private final BillService billService;
	private final CreditHistoryService creditHistoryService;

	@Override
	public String getCommand() {
		return "/status";
	}

	@Override
	public String getDescription() {
		return "Mengecek Status Server dan Database serta user terdaftar";
	}

	/**
	 * Mengumpulkan statistik sistem secara paralel lalu menampilkannya dalam satu pesan ringkasan.
	 *
	 * <p>Data yang dikumpulkan mencakup jumlah user, jumlah riwayat kredit, jumlah tagihan,
	 * dan informasi beban sistem. Semua query dijalankan bersamaan menggunakan {@code Mono.zip}
	 * untuk efisiensi waktu. Waktu eksekusi total juga dicatat dan ditampilkan di pesan.</p>
	 *
	 * @param update objek update dari Telegram yang berisi informasi pengirim
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah pesan status berhasil dikirim
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		long chatId = update.message.chatId;
		long startTime = System.currentTimeMillis();

		return Mono.zip(
				userService.countUsers(),
				creditHistoryService.countCreditHistory(),
				billService.countAllBills(),
				Mono.fromCallable(() -> new SystemUtils().getSystemUtils())
			)
			.flatMap(tuple -> {
				long executionTime = System.currentTimeMillis() - startTime;
				String statusMessage = buildStatusMessage(
					tuple.getT1(),
					tuple.getT2(),
					tuple.getT3(),
					tuple.getT4(),
					executionTime);
				return Mono.fromRunnable(() -> sendMessage(chatId, statusMessage, client));
			})
			.onErrorResume(e -> {
				log.error("Error processing status command", e);
				return Mono.fromRunnable(() -> sendMessage(chatId, "❌ Error mengambil data status. Silakan coba lagi.", client));
			})
			.then();
	}

	/**
	 * Menyusun pesan status sistem dalam format yang rapi dan mudah dibaca.
	 *
	 * <p>Pesan yang dihasilkan berisi tabel statistik data (jumlah user, kredit, tagihan, beban sistem),
	 * informasi kesehatan server, tips singkat penggunaan bot, dan waktu yang dibutuhkan
	 * untuk menghasilkan laporan ini.</p>
	 *
	 * @param totalUsers    jumlah total user yang terdaftar di sistem
	 * @param credit        jumlah total data riwayat kredit
	 * @param totalBills    jumlah total tagihan aktif
	 * @param systemLoad    string informasi beban CPU/memori dari {@link SystemUtils}
	 * @param executionTime waktu yang dibutuhkan untuk mengambil semua data (dalam milidetik)
	 * @return string pesan status yang sudah terformat dengan Markdown siap dikirim ke Telegram
	 */
	private String buildStatusMessage(long totalUsers, long credit, long totalBills, String systemLoad, long executionTime) {
		return String.format("""
                ⚡️ *PELUNASAN BOT STATUS*
                ╔══════════════════════
                ║ 🤖 Status: *ONLINE*
                ╠══════════════════════

                📊 *STATISTIK SISTEM*
                ┌────────────────────
                │ 👥 Users     : %d
                │ 📦 All Krd   : %d
                │ 💳 Tagihan   : %d
                │ ⚙️ Load      : %s
                └────────────────────

                📡 *INFORMASI SERVER*
                ┌────────────────────
                │ 🔋 Health     : 100%%
                └────────────────────

                🎯 *QUICK TIPS*
                ┌────────────────────
                │ • Ketik /help untuk bantuan
                │ • Cek status setiap hari
                │ • Update data secara rutin
                └────────────────────

                ✨ _System is healthy and ready!_
                ⏱️ _Generated in %dms_
                """,
			totalUsers, credit, totalBills, systemLoad, executionTime
		);
	}
}
