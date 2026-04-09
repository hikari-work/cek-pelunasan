package org.cekpelunasan.configuration

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType.LaunchOptions
import com.microsoft.playwright.Playwright
import jakarta.annotation.PreDestroy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

/**
 * Mengelola kumpulan (pool) instance browser Playwright agar scraping bisa berjalan
 * secara paralel tanpa membuat browser baru setiap saat.
 * 
 * 
 * Membuat browser baru itu mahal — perlu waktu dan memori. Dengan pool, kita siapkan
 * sejumlah browser di awal lalu pinjam-kembalikan saat dibutuhkan. Kalau semua browser
 * sedang terpakai, pemanggil akan menunggu (blocking) sampai ada yang dikembalikan.
 * 
 * 
 * 
 * Pool juga menangani kasus browser yang crash atau terputus: saat browser
 * dikembalikan dalam kondisi tidak tersambung, pool otomatis menggantinya dengan
 * instance browser yang segar.
 * 
 * 
 * 
 * Ukuran pool saat ini: {@value #POOL_SIZE} browser. Angka ini bisa disesuaikan
 * tergantung kebutuhan konkurensi dan kapasitas memori server.
 * 
 */
@Component
class PlaywrightBrowserPool(private val launchOptions: LaunchOptions?) {
    private val playwright: Playwright
    private val pool: BlockingQueue<Browser> = ArrayBlockingQueue<Browser>(POOL_SIZE)

    /**
     * Membuat instance Playwright, meluncurkan sejumlah browser sesuai [.POOL_SIZE],
     * lalu mengisi pool dengan browser-browser tersebut.
     * 
     * 
     * Constructor ini dipanggil otomatis oleh Spring saat aplikasi start. Jika ada browser
     * yang gagal diluncurkan, exception akan dilempar dan aplikasi tidak akan start.
     * 
     * 
     * @param launchOptions opsi peluncuran browser yang dikonfigurasi di [PlaywrightConfiguration]
     */
    init {
        this.playwright = Playwright.create()
        for (i in 0..<POOL_SIZE) {
            pool.offer(launchBrowser())
        }
        log.info("Playwright browser pool initialized with {} instances", POOL_SIZE)
    }

    /**
     * Mengambil satu browser dari pool untuk dipakai.
     * 
     * 
     * Jika semua browser sedang terpakai, method ini akan menunggu (blocking) sampai
     * ada browser yang dikembalikan ke pool lewat [.release].
     * Pastikan selalu memanggil `release()` setelah selesai, idealnya
     * di dalam blok `finally`.
     * 
     * 
     * @return instance [Browser] yang siap dipakai
     * @throws InterruptedException jika thread yang menunggu di-interrupt
     */
    @Throws(InterruptedException::class)
    fun acquire(): Browser {
        return pool.take()
    }

    /**
     * Mengembalikan browser yang sudah selesai dipakai ke pool.
     * 
     * 
     * Jika browser masih tersambung dengan baik, langsung dikembalikan ke antrian.
     * Tapi kalau browser crash atau terputus, pool akan meluncurkan browser baru
     * sebagai penggantinya sehingga jumlah browser di pool tetap terjaga.
     * 
     * 
     * @param browser instance browser yang ingin dikembalikan, boleh `null`
     * (akan diabaikan dan diganti dengan browser baru)
     */
    fun release(browser: Browser?) {
        if (browser != null && browser.isConnected()) {
            pool.offer(browser)
        } else {
            log.warn("Browser disconnected or null, replacing with fresh instance")
            synchronized(this) {
                try {
                    pool.offer(launchBrowser())
                } catch (e: Exception) {
                    log.error("Failed to replace crashed browser in pool", e)
                }
            }
        }
    }

    /**
     * Meluncurkan satu instance browser Chromium baru menggunakan opsi yang sudah dikonfigurasi.
     * 
     * 
     * Method ini disinkronisasi karena Playwright tidak thread-safe untuk operasi pembuatan browser.
     * 
     * 
     * @return instance [Browser] baru yang sudah siap dipakai
     */
    @Synchronized
    private fun launchBrowser(): Browser? {
        return playwright.chromium().launch(launchOptions)
    }

    /**
     * Menutup semua browser di pool dan menghentikan instance Playwright saat aplikasi shutdown.
     * 
     * 
     * Method ini dipanggil otomatis oleh Spring sebelum aplikasi berhenti. Semua error
     * saat menutup browser diabaikan agar proses shutdown tidak tersangkut.
     * 
     */
    @PreDestroy
    fun shutdown() {
        var b: Browser?
        while ((pool.poll().also { b = it }) != null) {
            try {
                b!!.close()
            } catch (ignored: Exception) {
            }
        }
        try {
            playwright.close()
        } catch (ignored: Exception) {
        }
        log.info("Playwright browser pool shut down")
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(PlaywrightBrowserPool::class.java)

        /** Jumlah browser yang disiapkan sekaligus di dalam pool.  */
        private const val POOL_SIZE = 3
    }
}
