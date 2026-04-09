package org.cekpelunasan.configuration

import com.microsoft.playwright.BrowserType.LaunchOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Menyediakan konfigurasi dasar untuk Playwright yang dipakai saat melakukan web scraping.
 * 
 * 
 * Class ini hanya bertanggung jawab mendefinisikan opsi peluncuran browser — yaitu
 * berjalan dalam mode headless (tanpa tampilan antarmuka visual). Opsi ini kemudian
 * di-inject ke [PlaywrightBrowserPool] yang mengelola pool instance browser.
 * 
 * 
 * 
 * Mode headless dipilih karena aplikasi ini berjalan di server tanpa display,
 * dan kita tidak perlu melihat browser-nya secara langsung.
 * 
 */
@Configuration
class PlaywrightConfiguration {
    /**
     * Mendefinisikan opsi peluncuran browser Playwright dalam mode headless.
     * 
     * 
     * Bean ini dipakai oleh [PlaywrightBrowserPool] untuk meluncurkan
     * setiap instance browser di dalam pool. Dengan memisahkan opsi ini ke
     * bean tersendiri, mudah untuk mengubah konfigurasi browser (misalnya
     * menambah argument) tanpa menyentuh logika pool.
     * 
     * 
     * @return opsi peluncuran browser dengan headless diaktifkan
     */
    @Bean
    fun playwrightLaunchOptions(): LaunchOptions? {
        return LaunchOptions().setHeadless(true)
    }
}
