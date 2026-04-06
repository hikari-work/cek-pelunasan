package org.cekpelunasan.configuration;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Menyediakan konfigurasi dasar untuk Playwright yang dipakai saat melakukan web scraping.
 * <p>
 * Class ini hanya bertanggung jawab mendefinisikan opsi peluncuran browser — yaitu
 * berjalan dalam mode headless (tanpa tampilan antarmuka visual). Opsi ini kemudian
 * di-inject ke {@link PlaywrightBrowserPool} yang mengelola pool instance browser.
 * </p>
 * <p>
 * Mode headless dipilih karena aplikasi ini berjalan di server tanpa display,
 * dan kita tidak perlu melihat browser-nya secara langsung.
 * </p>
 */
@Configuration
public class PlaywrightConfiguration {

	/**
	 * Mendefinisikan opsi peluncuran browser Playwright dalam mode headless.
	 * <p>
	 * Bean ini dipakai oleh {@link PlaywrightBrowserPool} untuk meluncurkan
	 * setiap instance browser di dalam pool. Dengan memisahkan opsi ini ke
	 * bean tersendiri, mudah untuk mengubah konfigurasi browser (misalnya
	 * menambah argument) tanpa menyentuh logika pool.
	 * </p>
	 *
	 * @return opsi peluncuran browser dengan headless diaktifkan
	 */
	@Bean
	public BrowserType.LaunchOptions playwrightLaunchOptions() {
		return new BrowserType.LaunchOptions().setHeadless(true);
	}
}
