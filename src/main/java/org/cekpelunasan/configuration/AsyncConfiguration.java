package org.cekpelunasan.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Mengatur cara aplikasi menjalankan tugas secara asynchronous (tidak blocking).
 * <p>
 * Karena hampir semua pekerjaan di sini adalah I/O-bound (panggil API, baca database, dll.),
 * kita pakai <em>virtual threads</em> dari Java 21 — bukan thread biasa. Virtual thread jauh
 * lebih ringan: satu aplikasi bisa punya jutaan virtual thread tanpa kehabisan memori,
 * karena JVM yang mengelola penjadualan-nya secara internal.
 * </p>
 * <p>
 * Setiap method yang diberi anotasi {@code @Async} di seluruh aplikasi ini akan berjalan
 * menggunakan executor yang dikonfigurasi di sini.
 * </p>
 */
@Configuration
@EnableAsync
@org.springframework.scheduling.annotation.EnableScheduling
public class AsyncConfiguration implements AsyncConfigurer {


	/**
	 * Menentukan executor yang dipakai saat menjalankan method {@code @Async}.
	 * <p>
	 * Setiap task yang masuk akan mendapat virtual thread-nya sendiri — dibuat on-demand
	 * dan langsung dibuang setelah selesai. Tidak perlu pusing soal ukuran thread pool
	 * karena JVM yang atur segalanya.
	 * </p>
	 *
	 * @return executor berbasis virtual thread, satu thread per task
	 */
	@Override
	public Executor getAsyncExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

}
