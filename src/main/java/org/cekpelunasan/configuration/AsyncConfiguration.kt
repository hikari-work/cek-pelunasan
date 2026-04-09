package org.cekpelunasan.configuration

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Mengatur cara aplikasi menjalankan tugas secara asynchronous (tidak blocking).
 * 
 * 
 * Karena hampir semua pekerjaan di sini adalah I/O-bound (panggil API, baca database, dll.),
 * kita pakai *virtual threads* dari Java 21 — bukan thread biasa. Virtual thread jauh
 * lebih ringan: satu aplikasi bisa punya jutaan virtual thread tanpa kehabisan memori,
 * karena JVM yang mengelola penjadualan-nya secara internal.
 * 
 * 
 * 
 * Setiap method yang diberi anotasi `@Async` di seluruh aplikasi ini akan berjalan
 * menggunakan executor yang dikonfigurasi di sini.
 * 
 */
@Configuration
@EnableAsync
@EnableScheduling
class AsyncConfiguration : AsyncConfigurer {
    /**
     * Menentukan executor yang dipakai saat menjalankan method `@Async`.
     * 
     * 
     * Setiap task yang masuk akan mendapat virtual thread-nya sendiri — dibuat on-demand
     * dan langsung dibuang setelah selesai. Tidak perlu pusing soal ukuran thread pool
     * karena JVM yang atur segalanya.
     * 
     * 
     * @return executor berbasis virtual thread, satu thread per task
     */
    override fun getAsyncExecutor(): Executor? {
        return Executors.newVirtualThreadPerTaskExecutor()
    }
}
