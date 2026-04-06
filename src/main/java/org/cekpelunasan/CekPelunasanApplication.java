package org.cekpelunasan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Titik masuk utama aplikasi Cek Pelunasan.
 * <p>
 * Aplikasi ini adalah bot hybrid yang melayani pengecekan status pelunasan pinjaman
 * melalui dua platform sekaligus: Telegram dan WhatsApp. "Hybrid" di sini berarti
 * satu aplikasi Spring Boot yang menjalankan kedua integrasi tersebut secara bersamaan.
 * </p>
 * <p>
 * Dua fitur Spring yang diaktifkan di sini:
 * <ul>
 *   <li>{@code @EnableAsync} — memungkinkan method berjalan di background tanpa memblokir
 *       thread utama, penting untuk operasi I/O seperti panggilan API dan query database</li>
 *   <li>{@code @EnableScheduling} — mengaktifkan task terjadwal (cron job) yang berjalan
 *       otomatis, misalnya untuk sinkronisasi data atau pengiriman notifikasi berkala</li>
 * </ul>
 * </p>
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class CekPelunasanApplication {

	/**
	 * Menjalankan aplikasi Spring Boot.
	 *
	 * @param args argumen baris perintah yang diteruskan ke Spring (bisa untuk override properties)
	 */
	public static void main(String[] args) {
		SpringApplication.run(CekPelunasanApplication.class, args);
	}

}
