package org.cekpelunasan.core.service.users;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.core.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Mengelola data pengguna bot yang terdaftar di sistem. Setiap pengguna
 * diidentifikasi menggunakan chat ID Telegram-nya, dan memiliki peran
 * (role) serta cabang yang menjadi tanggung jawabnya.
 *
 * <p>Class ini menjadi titik utama untuk operasi CRUD pengguna: mendaftarkan
 * pengguna baru, menghapus pengguna, dan menyimpan/mengambil preferensi
 * seperti cabang yang dipilih.</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	/**
	 * Mendaftarkan pengguna baru dengan chat ID yang diberikan. Peran default
	 * yang ditetapkan adalah {@link AccountOfficerRoles#AO} (Account Officer).
	 * Jika sudah ada pengguna dengan chat ID yang sama, data akan ditimpa.
	 *
	 * @param chatId ID chat Telegram pengguna yang ingin didaftarkan
	 * @return {@link Mono} yang selesai ketika pengguna berhasil disimpan
	 */
	@SuppressWarnings("null")
	public Mono<Void> insertNewUsers(@NonNull Long chatId) {
		return userRepository.save(User.builder().chatId(chatId).roles(AccountOfficerRoles.AO).build()).then();
	}

	/**
	 * Menghitung total pengguna yang terdaftar di database.
	 * Berguna untuk laporan atau monitoring admin.
	 *
	 * @return {@link Mono} berisi jumlah total pengguna
	 */
	public Mono<Long> countUsers() {
		return userRepository.count();
	}

	/**
	 * Mengambil seluruh daftar pengguna yang terdaftar.
	 *
	 * @return {@link Flux} berisi semua data pengguna
	 */
	public Flux<User> findAllUsers() {
		return userRepository.findAll();
	}

	/**
	 * Menghapus pengguna berdasarkan chat ID-nya dari database.
	 * Setelah dihapus, pengguna tidak bisa lagi mengakses bot kecuali
	 * didaftarkan ulang oleh admin.
	 *
	 * @param chatId ID chat Telegram pengguna yang ingin dihapus
	 * @return {@link Mono} yang selesai ketika pengguna berhasil dihapus
	 */
	@SuppressWarnings("null")
	public Mono<Void> deleteUser(@NonNull Long chatId) {
		return userRepository.deleteById(chatId);
	}

	/**
	 * Mencari data lengkap pengguna berdasarkan chat ID-nya.
	 *
	 * @param chatId ID chat Telegram pengguna yang ingin dicari
	 * @return {@link Mono} berisi data pengguna, atau kosong jika tidak ditemukan
	 */
	@SuppressWarnings("null")
	public Mono<User> findUserByChatId(@NonNull Long chatId) {
		return userRepository.findById(chatId);
	}

	/**
	 * Mengambil kode cabang yang sudah dipilih oleh pengguna sebelumnya.
	 * Berguna agar pengguna tidak perlu memilih cabang setiap kali
	 * mengirim perintah ke bot.
	 *
	 * @param chatId ID chat Telegram pengguna
	 * @return {@link Mono} berisi kode cabang yang tersimpan, atau kosong jika belum diatur
	 */
	@SuppressWarnings("null")
	public Mono<String> findUserBranch(@NonNull Long chatId) {
		return userRepository.findById(chatId)
			.flatMap(user -> Mono.justOrEmpty(user.getBranch()));
	}

	/**
	 * Menyimpan pilihan cabang untuk pengguna tertentu. Data disimpan langsung
	 * ke dokumen pengguna di database, menggantikan cabang yang sebelumnya
	 * tersimpan jika ada.
	 *
	 * @param chatId ID chat Telegram pengguna
	 * @param branch kode cabang yang ingin disimpan sebagai pilihan pengguna
	 * @return {@link Mono} yang selesai ketika perubahan berhasil disimpan
	 */
	@SuppressWarnings("null")
	public Mono<Void> saveUserBranch(@NonNull Long chatId, String branch) {
		return userRepository.findById(chatId)
			.flatMap(user -> {
				user.setBranch(branch);
				return userRepository.save(user);
			})
			.then();
	}
}
