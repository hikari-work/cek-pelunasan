package org.cekpelunasan.configuration;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Mengelola kumpulan (pool) instance browser Playwright agar scraping bisa berjalan
 * secara paralel tanpa membuat browser baru setiap saat.
 * <p>
 * Membuat browser baru itu mahal — perlu waktu dan memori. Dengan pool, kita siapkan
 * sejumlah browser di awal lalu pinjam-kembalikan saat dibutuhkan. Kalau semua browser
 * sedang terpakai, pemanggil akan menunggu (blocking) sampai ada yang dikembalikan.
 * </p>
 * <p>
 * Pool juga menangani kasus browser yang crash atau terputus: saat browser
 * dikembalikan dalam kondisi tidak tersambung, pool otomatis menggantinya dengan
 * instance browser yang segar.
 * </p>
 * <p>
 * Ukuran pool saat ini: {@value #POOL_SIZE} browser. Angka ini bisa disesuaikan
 * tergantung kebutuhan konkurensi dan kapasitas memori server.
 * </p>
 */
@Component
public class PlaywrightBrowserPool {

	private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowserPool.class);

	/** Jumlah browser yang disiapkan sekaligus di dalam pool. */
	private static final int POOL_SIZE = 3;

	private final Playwright playwright;
	private final BrowserType.LaunchOptions launchOptions;
	private final BlockingQueue<Browser> pool = new ArrayBlockingQueue<>(POOL_SIZE);

	/**
	 * Membuat instance Playwright, meluncurkan sejumlah browser sesuai {@link #POOL_SIZE},
	 * lalu mengisi pool dengan browser-browser tersebut.
	 * <p>
	 * Constructor ini dipanggil otomatis oleh Spring saat aplikasi start. Jika ada browser
	 * yang gagal diluncurkan, exception akan dilempar dan aplikasi tidak akan start.
	 * </p>
	 *
	 * @param playwrightLaunchOptions opsi peluncuran browser yang dikonfigurasi di {@link PlaywrightConfiguration}
	 */
	public PlaywrightBrowserPool(BrowserType.LaunchOptions playwrightLaunchOptions) {
		this.playwright = Playwright.create();
		this.launchOptions = playwrightLaunchOptions;
		for (int i = 0; i < POOL_SIZE; i++) {
			pool.offer(launchBrowser());
		}
		log.info("Playwright browser pool initialized with {} instances", POOL_SIZE);
	}

	/**
	 * Mengambil satu browser dari pool untuk dipakai.
	 * <p>
	 * Jika semua browser sedang terpakai, method ini akan menunggu (blocking) sampai
	 * ada browser yang dikembalikan ke pool lewat {@link #release(Browser)}.
	 * Pastikan selalu memanggil {@code release()} setelah selesai, idealnya
	 * di dalam blok {@code finally}.
	 * </p>
	 *
	 * @return instance {@link Browser} yang siap dipakai
	 * @throws InterruptedException jika thread yang menunggu di-interrupt
	 */
	public Browser acquire() throws InterruptedException {
		return pool.take();
	}

	/**
	 * Mengembalikan browser yang sudah selesai dipakai ke pool.
	 * <p>
	 * Jika browser masih tersambung dengan baik, langsung dikembalikan ke antrian.
	 * Tapi kalau browser crash atau terputus, pool akan meluncurkan browser baru
	 * sebagai penggantinya sehingga jumlah browser di pool tetap terjaga.
	 * </p>
	 *
	 * @param browser instance browser yang ingin dikembalikan, boleh {@code null}
	 *                (akan diabaikan dan diganti dengan browser baru)
	 */
	public void release(Browser browser) {
		if (browser != null && browser.isConnected()) {
			pool.offer(browser);
		} else {
			log.warn("Browser disconnected or null, replacing with fresh instance");
			synchronized (this) {
				try {
					pool.offer(launchBrowser());
				} catch (Exception e) {
					log.error("Failed to replace crashed browser in pool", e);
				}
			}
		}
	}

	/**
	 * Meluncurkan satu instance browser Chromium baru menggunakan opsi yang sudah dikonfigurasi.
	 * <p>
	 * Method ini disinkronisasi karena Playwright tidak thread-safe untuk operasi pembuatan browser.
	 * </p>
	 *
	 * @return instance {@link Browser} baru yang sudah siap dipakai
	 */
	private synchronized Browser launchBrowser() {
		return playwright.chromium().launch(launchOptions);
	}

	/**
	 * Menutup semua browser di pool dan menghentikan instance Playwright saat aplikasi shutdown.
	 * <p>
	 * Method ini dipanggil otomatis oleh Spring sebelum aplikasi berhenti. Semua error
	 * saat menutup browser diabaikan agar proses shutdown tidak tersangkut.
	 * </p>
	 */
	@PreDestroy
	public void shutdown() {
		Browser b;
		while ((b = pool.poll()) != null) {
			try { b.close(); } catch (Exception ignored) {}
		}
		try { playwright.close(); } catch (Exception ignored) {}
		log.info("Playwright browser pool shut down");
	}
}
