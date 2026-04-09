package org.cekpelunasan.core.lifecycle;

import lombok.NonNull;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Inisialisasi awal yang dijalankan tepat setelah aplikasi berhasil start.
 * <p>
 * Class ini bertugas memastikan akun owner bot selalu terdaftar sebagai ADMIN
 * setiap kali aplikasi dinyalakan. Jika akun owner belum ada di database,
 * akan dibuat baru. Jika sudah ada, data yang lama akan ditimpa (upsert).
 * </p>
 * <p>
 * Chat ID owner bot dibaca dari konfigurasi {@code telegram.bot.owner} di
 * {@code application.properties} atau environment variable, sehingga tidak
 * perlu di-hardcode di dalam kode.
 * </p>
 */
@Component
public class PreRun {

	private static final Logger log = LoggerFactory.getLogger(PreRun.class);

	/**
	 * Chat ID Telegram milik owner bot yang akan selalu dijamin punya akses ADMIN.
	 * Dibaca dari property {@code telegram.bot.owner} saat aplikasi start.
	 */
	private final Long botOwner;

	private final UserRepository userRepository;

	/**
	 * Membangun komponen PreRun dengan mengambil chat ID owner dari konfigurasi.
	 *
	 * @param userRepository repository untuk menyimpan data pengguna ke MongoDB
	 * @param botOwner       chat ID Telegram owner bot, dibaca dari {@code telegram.bot.owner}
	 */
	public PreRun(UserRepository userRepository, @Value("${telegram.bot.owner}") String botOwner) {
		this.botOwner = Long.parseLong(botOwner);
		this.userRepository = userRepository;
	}

	/**
	 * Menyimpan atau memperbarui data akun owner bot sebagai ADMIN di database.
	 * <p>
	 * Method ini dipanggil otomatis oleh Spring tepat setelah seluruh konteks aplikasi
	 * siap ({@link ApplicationReadyEvent}). Dengan cara ini, owner bot dijamin selalu
	 * bisa mengakses bot meskipun database di-reset atau data pengguna terhapus.
	 * </p>
	 */
	@EventListener(ApplicationReadyEvent.class)
	@SuppressWarnings("null")
	public void initData() {
		log.info("Initializing data...");
		@NonNull
		User user = User.builder()
				.chatId(botOwner)
				.userCode("ADMIN")
				.roles(AccountOfficerRoles.ADMIN)
				.build();
		userRepository.save(user)
				.doOnSuccess(u -> log.info("Owner {} saved as ADMIN", botOwner))
				.doOnError(e -> log.error("Gagal menyimpan owner", e))
				.subscribe();
	}
}
