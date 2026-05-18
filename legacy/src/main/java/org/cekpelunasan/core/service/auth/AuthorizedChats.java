package org.cekpelunasan.core.service.auth;

import lombok.NonNull;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Penjaga gerbang akses bot — class ini menyimpan daftar chat ID yang boleh
 * menggunakan bot. Bayangkan seperti daftar tamu di acara: kalau nama kamu
 * tidak ada di sini, kamu tidak bisa masuk.
 *
 * <p>Daftar ini disimpan di memori (in-memory) menggunakan {@link ConcurrentHashMap}
 * supaya aman diakses dari banyak thread sekaligus. Saat aplikasi pertama kali
 * nyala, daftar ini langsung diisi dari database agar tidak perlu login ulang.</p>
 */
@Component
public class AuthorizedChats {

	private static final Logger log = LoggerFactory.getLogger(AuthorizedChats.class);

	private final UserRepository userRepository;
	Set<Long> authorizedChats = ConcurrentHashMap.newKeySet();

	public AuthorizedChats(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * Mengecek apakah chat ID tertentu sudah terdaftar sebagai pengguna yang
	 * diizinkan mengakses bot.
	 *
	 * @param chatId ID unik chat Telegram yang ingin dicek
	 * @return {@code true} kalau chat ID ada di daftar, {@code false} kalau belum
	 */
	public boolean isAuthorized(Long chatId) {
		return authorizedChats.contains(chatId);
	}

	public int size() {
		return authorizedChats.size();
	}

	/**
	 * Menambahkan chat ID baru ke daftar yang diizinkan. Biasanya dipanggil
	 * setelah admin mendaftarkan pengguna baru.
	 *
	 * @param chatId ID chat Telegram yang ingin ditambahkan
	 */
	public void addAuthorizedChat(Long chatId) {
		authorizedChats.add(chatId);
	}

	/**
	 * Menghapus chat ID dari daftar yang diizinkan, efektif mencabut akses
	 * pengguna tersebut tanpa perlu restart aplikasi.
	 *
	 * @param chatId ID chat Telegram yang ingin dicabut aksesnya
	 */
	public void deleteUser(Long chatId) {
		authorizedChats.remove(chatId);
	}

	/**
	 * Mengambil peran (role) dari pengguna berdasarkan chat ID-nya. Berguna
	 * saat bot perlu memutuskan apakah pengguna boleh menjalankan perintah
	 * tertentu yang hanya boleh dilakukan oleh peran tertentu.
	 *
	 * @param chatId ID chat Telegram yang ingin dicari perannya
	 * @return {@link Mono} berisi peran pengguna, atau kosong kalau pengguna
	 *         tidak ditemukan atau belum punya peran
	 */
	public Mono<AccountOfficerRoles> getUserRoles(@NonNull Long chatId) {
		return userRepository.findById(chatId).mapNotNull(User::getRoles);
	}

	/**
	 * Memuat semua pengguna dari database ke dalam memori saat aplikasi selesai
	 * startup. Ini dilakukan sekali saja agar pengguna yang sudah terdaftar
	 * sebelumnya tidak perlu didaftarkan ulang setiap kali server restart.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void preRun() {
		userRepository.findAll()
				.map(User::getChatId)
				.doOnNext(authorizedChats::add)
				.doOnComplete(() -> log.info("[AuthorizedChats] Loaded {} authorized chats", authorizedChats.size()))
				.doOnError(e -> log.error("[AuthorizedChats] Gagal memuat authorized chats", e))
				.subscribe();
	}
}
