package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationSavingsButton;
import org.cekpelunasan.platform.telegram.callback.pagination.SelectSavingsBranch;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.SavingsUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /tab} — mencari data tabungan nasabah berdasarkan nama.
 *
 * <p>Perintah ini memungkinkan user mencari tabungan nasabah dengan memasukkan nama.
 * Format penggunaan: {@code /tab <nama_nasabah>}, misalnya {@code /tab Budi Santoso}.</p>
 *
 * <p>Pencarian mempertimbangkan kode cabang user yang login:</p>
 * <ul>
 *   <li>Jika user memiliki kode cabang terdaftar: pencarian langsung difilter berdasarkan cabang tersebut.</li>
 *   <li>Jika user belum punya kode cabang: bot menampilkan semua cabang yang memiliki
 *       nasabah dengan nama tersebut, lalu user memilih cabang yang diinginkan
 *       melalui tombol inline.</li>
 * </ul>
 *
 * <p>Hasil ditampilkan dengan paginasi menggunakan tombol inline. Bisa diakses oleh admin, AO, dan pimpinan.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SavingsFindCommandHandler extends AbstractCommandHandler {

	private final SavingsService savingsService;
	private final SelectSavingsBranch selectSavingsBranch;
	private final UserService userService;
	private final PaginationSavingsButton paginationSavingsButton;
	private final SavingsUtils savingsUtils;

	@Override
	public String getCommand() {
		return "/tab";
	}

	@Override
	public String getDescription() {
		return "";
	}

	/**
	 * Memvalidasi peran pengguna sebelum melakukan pencarian tabungan.
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
	 * Mencari data tabungan berdasarkan nama nasabah, dengan mempertimbangkan cabang user.
	 *
	 * <p>Alur pencarian:</p>
	 * <ol>
	 *   <li>Nama diambil dari teks setelah {@code /tab }. Jika kosong, bot meminta user mengisi nama.</li>
	 *   <li>Dicari kode cabang yang terdaftar untuk user yang login.</li>
	 *   <li>Jika kode cabang ditemukan: langsung cari tabungan dengan nama dan cabang tersebut,
	 *       tampilkan hasilnya dengan paginasi.</li>
	 *   <li>Jika tidak ada kode cabang: tampilkan daftar cabang yang punya nasabah dengan nama itu,
	 *       biarkan user memilih cabangnya sendiri melalui tombol inline.</li>
	 * </ol>
	 *
	 * @param chatId ID chat pengguna yang mengirim perintah
	 * @param text   teks lengkap perintah termasuk nama nasabah yang dicari
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah hasil pencarian dikirim ke user
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String name = text.replace("/tab ", "").trim();
		if (name.isEmpty() || name.equals("/tab")) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "Nama Harus Diisi", client));
		}
		return userService.findUserBranch(chatId)
			.flatMap(userBranch -> savingsService.findByNameAndBranch(name, userBranch, 0)
				.flatMap(byNameAndBranch -> Mono.fromRunnable(() -> {
					if (byNameAndBranch.isEmpty()) {
						sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
						return;
					}
					sendMessage(chatId,
						savingsUtils.buildMessage(byNameAndBranch, 0, System.currentTimeMillis()),
						paginationSavingsButton.keyboardMarkup(byNameAndBranch, userBranch, 0, name),
						client);
				})))
			.switchIfEmpty(
				savingsService.listAllBranch(name)
					.flatMap(branches -> Mono.fromRunnable(() -> {
						if (branches == null || branches.isEmpty()) {
							sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
							return;
						}
						sendMessage(chatId, "Data ditemukan dalam beberapa cabang, pilih cabang:", selectSavingsBranch.dynamicSelectBranch(branches, name), client);
					}))
			)
			.onErrorResume(e -> {
				log.error("Error /tab chatId={} text='{}': {}", chatId, text, e.getMessage(), e);
				return Mono.fromRunnable(() -> sendMessage(chatId, "❌ Terjadi kesalahan, coba lagi", client));
			})
			.then();
	}
}
