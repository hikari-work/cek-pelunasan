package org.cekpelunasan.configuration;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.impl.TargetClosedError;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Mengelola kumpulan (pool) instance browser Playwright agar PDF bisa dirender
 * secara paralel tanpa membuat browser baru setiap saat.
 *
 * <h3>Thread-safety</h3>
 * <p>
 * Playwright <strong>tidak thread-safe</strong> — objek Playwright hanya boleh diakses
 * dari thread yang membuatnya. Untuk itu, setiap slot di pool memiliki
 * {@link ExecutorService} satu-thread tersendiri. Semua operasi Playwright
 * (termasuk pembuatan, penggunaan, dan penggantian) selalu berjalan di thread
 * khusus milik slot tersebut.
 * </p>
 *
 * <h3>Lifecycle sebuah slot</h3>
 * <ol>
 *   <li>Saat startup: {@code Playwright.create()} + {@code chromium().launch()} dijalankan
 *       di thread khusus slot tersebut.</li>
 *   <li>Saat {@link #withBrowser}: slot diambil dari antrian; koneksi dicek di thread khusus;
 *       jika mati, direinisialisasi sebelum operasi dijalankan.</li>
 *   <li>Setelah operasi selesai (sukses maupun gagal): slot dikembalikan ke antrian.</li>
 *   <li>Saat shutdown: semua browser dan Playwright ditutup di thread khusus masing-masing.</li>
 * </ol>
 */
@Component
public class PlaywrightBrowserPool {

	private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowserPool.class);

	/** Jumlah slot browser yang disiapkan sekaligus di dalam pool. */
	private static final int POOL_SIZE = 3;

	/** Batas waktu tunggu saat semua browser sedang terpakai, dalam detik. */
	private static final int ACQUIRE_TIMEOUT_SECONDS = 30;

	/** Batas waktu untuk satu operasi Playwright (safety net), dalam detik. */
	private static final int OPERATION_TIMEOUT_SECONDS = 90;

	private final BrowserType.LaunchOptions launchOptions;
	private final BlockingQueue<BrowserSlot> pool = new ArrayBlockingQueue<>(POOL_SIZE);
	private final List<BrowserSlot> allSlots = new ArrayList<>();

	/**
	 * Operasi yang dijalankan menggunakan instance {@link Browser} dari pool.
	 */
	@FunctionalInterface
	public interface BrowserOperation<T> {
		T execute(Browser browser) throws Exception;
	}

	/**
	 * Membuat pool: tiap slot mendapat thread khusus dan pasangan (Playwright, Browser) tersendiri.
	 *
	 * @param playwrightLaunchOptions opsi peluncuran browser dari {@link PlaywrightConfiguration}
	 */
	public PlaywrightBrowserPool(BrowserType.LaunchOptions playwrightLaunchOptions) {
		this.launchOptions = playwrightLaunchOptions;
		for (int i = 0; i < POOL_SIZE; i++) {
			try {
				BrowserSlot slot = new BrowserSlot(i, launchOptions);
				pool.offer(slot);
				allSlots.add(slot);
			} catch (Exception e) {
				log.error("Gagal membuat browser slot #{} saat inisialisasi pool", i, e);
			}
		}
		log.info("Playwright browser pool diinisialisasi dengan {} slot", pool.size());
	}

	/**
	 * Mengeksekusi operasi browser menggunakan slot yang tersedia dari pool.
	 * <p>
	 * Semua operasi Playwright berjalan di thread khusus milik slot, memastikan
	 * thread-safety Playwright terpenuhi. Jika browser dalam slot terputus saat
	 * akan dipakai, browser direinisialisasi secara otomatis di thread yang sama
	 * sebelum operasi dijalankan.
	 * </p>
	 *
	 * @param operation operasi yang akan dijalankan menggunakan {@link Browser}
	 * @param <T>       tipe nilai yang dikembalikan oleh operasi
	 * @return hasil dari operasi
	 * @throws InterruptedException  jika thread diinterupsi saat menunggu slot tersedia
	 * @throws IllegalStateException jika tidak ada slot tersedia setelah timeout
	 * @throws Exception             jika operasi melempar exception
	 */
	public <T> T withBrowser(BrowserOperation<T> operation) throws Exception {
		BrowserSlot slot = pool.poll(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		if (slot == null) {
			throw new IllegalStateException(
				"Tidak ada browser tersedia di pool setelah " + ACQUIRE_TIMEOUT_SECONDS + " detik");
		}
		try {
			return slot.execute(operation, launchOptions);
		} finally {
			pool.offer(slot);
		}
	}

	/**
	 * Menutup semua browser dan Playwright saat aplikasi shutdown.
	 */
	@PreDestroy
	public void shutdown() {
		for (BrowserSlot slot : allSlots) {
			slot.close();
		}
		log.info("Playwright browser pool dimatikan");
	}

	// -------------------------------------------------------------------------
	// Inner class: BrowserSlot
	// -------------------------------------------------------------------------

	private static class BrowserSlot {

		private static final Logger log = LoggerFactory.getLogger(BrowserSlot.class);

		private final int id;
		private final ExecutorService executor;
		private Playwright playwright;
		private Browser browser;

		/**
		 * Membuat slot baru: inisialisasi Playwright dan Browser di thread khusus.
		 */
		BrowserSlot(int id, BrowserType.LaunchOptions options) throws Exception {
			this.id = id;
			this.executor = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "playwright-browser-" + id);
				t.setDaemon(true);
				return t;
			});
			// Playwright dan Browser harus dibuat di thread yang sama yang akan memakainya
			executor.submit(() -> init(options)).get(30, TimeUnit.SECONDS);
		}

		int id() {
			return id;
		}

		/** Membuat pasangan baru (Playwright, Browser) di thread saat ini. */
		private void init(BrowserType.LaunchOptions options) {
			playwright = Playwright.create();
			browser = playwright.chromium().launch(options);
			log.debug("Browser slot {} diinisialisasi", id);
		}

		/** Menutup pasangan lama dan membuat yang baru, di thread saat ini. */
		private void reinit(BrowserType.LaunchOptions options) {
			try { browser.close(); } catch (Exception ignored) {}
			try { playwright.close(); } catch (Exception ignored) {}
			init(options);
			log.info("Browser slot {} direinisialisasi setelah disconnect/crash", id);
		}

		/**
		 * Menjalankan operasi di thread khusus slot ini.
		 * Jika browser terputus, direinisialisasi terlebih dahulu.
		 */
		<T> T execute(BrowserOperation<T> operation, BrowserType.LaunchOptions options) throws Exception {
			try {
				return executor.submit((Callable<T>) () -> {
					if (browser == null || !browser.isConnected()) {
						log.warn("Browser slot {} terputus, memperbarui...", id);
						reinit(options);
					}
					try {
						return operation.execute(browser);
					} catch (TargetClosedError tce) {
						log.warn("Browser slot {} crash saat operasi, reinit segera", id);
						reinit(options);
						throw tce;
					}
				}).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (ExecutionException e) {
				Throwable cause = e.getCause();
				if (cause instanceof Exception ex) throw ex;
				throw new RuntimeException(cause);
			}
		}

		/** Menutup browser dan Playwright, lalu shutdown executor. */
		void close() {
			try {
				executor.submit(() -> {
					try { browser.close(); } catch (Exception ignored) {}
					try { playwright.close(); } catch (Exception ignored) {}
				}).get(10, TimeUnit.SECONDS);
			} catch (Exception ignored) {
			} finally {
				executor.shutdownNow();
			}
		}
	}
}
