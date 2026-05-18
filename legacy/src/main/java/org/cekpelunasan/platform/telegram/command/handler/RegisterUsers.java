package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.repository.UserRepository;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.slik.SendNotificationSlikUpdated;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /otor} — mendaftarkan user sebagai AO atau pimpinan cabang.
 *
 * <p>Perintah ini digunakan untuk mengaitkan user Telegram dengan identitas mereka
 * di sistem, apakah sebagai Account Officer (AO) dengan kode 3 huruf, atau sebagai
 * pimpinan cabang dengan kode cabang numerik. Format penggunaan:</p>
 * <ul>
 *   <li>{@code /otor <kode_ao>} — mendaftarkan user sebagai AO, kode harus 3 karakter alfanumerik.</li>
 *   <li>{@code /otor <kode_cabang>} — mendaftarkan user sebagai pimpinan, kode harus numerik
 *       dan harus terdaftar dalam daftar cabang yang valid di database.</li>
 * </ul>
 *
 * <p>Setelah pendaftaran berhasil, sistem akan memicu notifikasi terkait pembaruan data SLIK
 * melalui {@link SendNotificationSlikUpdated}.</p>
 *
 * <p>Bisa diakses oleh admin, AO yang sudah terdaftar, dan pimpinan.</p>
 */
@Component
@RequiredArgsConstructor
public class RegisterUsers extends AbstractCommandHandler {

	private final BillService billService;
	private final UserService userService;
	private final UserRepository userRepository;
	private final SendNotificationSlikUpdated sendNotificationSlikUpdated;

	@Override
	public String getCommand() {
		return "/otor";
	}

	@Override
	public String getDescription() {
		return "Gunakan Command Ini untuk mendaftarkan user Berdasarkan User ID, Pimpinan atau AO";
	}

	/**
	 * Memvalidasi peran pengguna sebelum memproses pendaftaran.
	 *
	 * @param update objek update dari Telegram
	 * @param client koneksi aktif ke Telegram
	 * @return hasil pendaftaran, atau ditolak jika tidak punya izin
	 */
	@Override
	@RequireAuth(roles = {AccountOfficerRoles.AO, AccountOfficerRoles.PIMP, AccountOfficerRoles.ADMIN})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	/**
	 * Mendaftarkan user sebagai AO atau pimpinan berdasarkan kode yang diberikan.
	 *
	 * <p>Logika penentuan peran:</p>
	 * <ul>
	 *   <li>Jika argumen panjangnya 3 karakter: didaftarkan sebagai AO dengan kode tersebut.</li>
	 *   <li>Jika argumen berupa angka: dicek apakah kode tersebut ada di daftar cabang.
	 *       Jika ada, didaftarkan sebagai pimpinan; jika tidak, tidak melakukan apa-apa.</li>
	 *   <li>Selain itu: bot memberitahu format yang tidak valid.</li>
	 * </ul>
	 *
	 * @param chatId ID chat pengguna yang mengirim perintah
	 * @param text   teks lengkap perintah termasuk kode AO atau kode cabang
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah pendaftaran berhasil atau gagal dengan pesan error
	 */
	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String[] parts = text.split(" ");
		if (parts.length < 2) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "Gunakan /otor <kode cabang> atau\n/otor <kode ao>", client));
		}

		String target = parts[1];

		return userService.findUserByChatId(chatId)
			.switchIfEmpty(Mono.fromRunnable(() -> sendMessage(chatId, "User tidak ditemukan", client)))
			.flatMap(user -> {
				if (target.length() == 3) {
					return saveAndNotify(user, AccountOfficerRoles.AO, target, "AO", chatId, client);
				}
				if (isNumber(target)) {
					return billService.lisAllBranch()
						.flatMap(branchSet -> {
							if (branchSet.contains(target)) {
								return saveAndNotify(user, AccountOfficerRoles.PIMP, target, "Pimpinan", chatId, client);
							}
							return Mono.empty();
						});
				}
				sendMessage(chatId, "❌ *Format tidak valid*\n\nContoh: /otor 1234567890", client);
				return Mono.empty();
			})
			.then();
	}

	/**
	 * Menyimpan perubahan peran dan kode user ke database, lalu mengirim notifikasi konfirmasi.
	 *
	 * <p>Setelah berhasil disimpan, bot mengirim pesan konfirmasi ke user dan memicu
	 * proses notifikasi pembaruan data SLIK agar semua pihak yang relevan mendapat informasi terbaru.</p>
	 *
	 * @param user   objek user yang akan diperbarui datanya
	 * @param role   peran baru yang akan ditetapkan (AO atau PIMP)
	 * @param code   kode identifikasi baru (kode AO atau kode cabang)
	 * @param label  label peran untuk ditampilkan di pesan konfirmasi (misalnya "AO" atau "Pimpinan")
	 * @param chatId ID chat pengguna yang akan menerima konfirmasi
	 * @param client koneksi aktif ke Telegram
	 * @return {@link Mono} yang selesai setelah data tersimpan dan notifikasi terkirim
	 */
	private Mono<Void> saveAndNotify(User user, AccountOfficerRoles role, String code, String label, long chatId, SimpleTelegramClient client) {
		user.setUserCode(code);
		user.setRoles(role);
		return userRepository.save(user)
			.doOnSuccess(saved -> {
				sendMessage(chatId, "✅ User berhasil didaftarkan sebagai *" + label + "*", client);
				sendNotificationSlikUpdated.runTest();
			})
			.then();
	}

	/**
	 * Memeriksa apakah sebuah string dapat diurai sebagai angka panjang (long).
	 *
	 * @param str string yang akan diperiksa
	 * @return {@code true} jika string merupakan angka valid, {@code false} jika tidak
	 */
	private boolean isNumber(String str) {
		try {
			Long.parseLong(str);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
