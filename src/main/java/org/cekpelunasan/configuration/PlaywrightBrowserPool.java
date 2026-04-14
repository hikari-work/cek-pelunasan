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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Mengelola kumpulan (pool) instance browser Playwright agar scraping bisa berjalan
 * secara paralel tanpa membuat browser baru setiap saat.
 *
 * <h3>Thread-safety</h3>
 * <p>
 * Playwright <strong>tidak thread-safe</strong> — satu instance hanya boleh dipakai
 * dari thread pembuatnya. Untuk itu, setiap browser di dalam pool memiliki
 * instance {@link Playwright} <em>sendiri</em> yang dibuat bersamaan.
 * Jika satu browser crash, hanya pasangan (Playwright, Browser) miliknya yang
 * ditutup dan diganti; browser lain di pool tidak terpengaruh.
 * </p>
 *
 * <h3>Lifecycle sebuah slot</h3>
 * <ol>
 *   <li>Saat startup: {@code Playwright.create()} + {@code chromium().launch()} di thread pool.</li>
 *   <li>Saat {@link #acquire()}: ambil Browser dari antrian; cek koneksi; jika mati, ganti dulu.</li>
 *   <li>Saat {@link #release(Browser)}: jika masih tersambung, kembalikan ke antrian;
 *       jika terputus/crash, tutup Playwright-nya dan buat pasangan baru.</li>
 *   <li>Saat shutdown: tutup semua Browser dan Playwright secara berurutan.</li>
 * </ol>
 */
@Component
public class PlaywrightBrowserPool {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowserPool.class);

    /** Jumlah browser yang disiapkan sekaligus di dalam pool. */
    private static final int POOL_SIZE = 3;

    /** Batas waktu tunggu saat semua browser sedang terpakai, dalam detik. */
    private static final int ACQUIRE_TIMEOUT_SECONDS = 30;

    private final BrowserType.LaunchOptions launchOptions;

    /** Antrian browser yang siap dipakai. */
    private final BlockingQueue<Browser> pool = new ArrayBlockingQueue<>(POOL_SIZE);

    /**
     * Memetakan setiap Browser ke Playwright miliknya, sehingga saat sebuah
     * browser crash kita tahu Playwright mana yang harus ditutup.
     */
    private final ConcurrentHashMap<Browser, Playwright> playwrightMap = new ConcurrentHashMap<>();

    /**
     * Membuat pool: tiap slot mendapat pasangan (Playwright, Browser) tersendiri.
     *
     * @param playwrightLaunchOptions opsi peluncuran browser dari {@link PlaywrightConfiguration}
     */
    public PlaywrightBrowserPool(BrowserType.LaunchOptions playwrightLaunchOptions) {
        this.launchOptions = playwrightLaunchOptions;
        for (int i = 0; i < POOL_SIZE; i++) {
            try {
                Browser browser = createBrowserEntry();
                pool.offer(browser);
            } catch (Exception e) {
                log.error("Gagal membuat browser #{}  saat inisialisasi pool", i, e);
            }
        }
        log.info("Playwright browser pool diinisialisasi dengan {} instance", pool.size());
    }

    /**
     * Mengambil satu browser dari pool.
     * <p>
     * Browser yang diambil dari antrian langsung dicek koneksinya. Jika browser
     * ternyata sudah mati saat menganggur di pool (misalnya karena OOM di OS),
     * browser tersebut diganti sebelum dikembalikan ke pemanggil.
     * </p>
     * <p>
     * Jika semua browser sedang terpakai, method ini menunggu hingga
     * {@value #ACQUIRE_TIMEOUT_SECONDS} detik sebelum melempar exception.
     * </p>
     *
     * @return instance {@link Browser} yang siap dipakai dan terkonfirmasi tersambung
     * @throws InterruptedException     jika thread diinterupsi saat menunggu
     * @throws IllegalStateException    jika tidak ada browser tersedia setelah timeout
     */
    public Browser acquire() throws InterruptedException {
        while (true) {
            Browser browser = pool.poll(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (browser == null) {
                throw new IllegalStateException(
                    "Tidak ada browser tersedia di pool setelah " + ACQUIRE_TIMEOUT_SECONDS + " detik");
            }
            if (browser.isConnected()) {
                return browser;
            }
            // Browser mati saat menganggur di pool — ganti diam-diam
            log.warn("Browser ditemukan terputus di dalam pool, mengganti...");
            replaceBrowser(browser);
            // Lanjut iterasi: ambil browser berikutnya dari antrian
        }
    }

    /**
     * Mengembalikan browser yang sudah selesai dipakai ke pool.
     * <p>
     * Jika browser masih tersambung, langsung dikembalikan ke antrian.
     * Jika browser crash atau terputus (termasuk jika {@code null} diteruskan),
     * pasangan (Playwright, Browser) miliknya ditutup dan diganti dengan yang baru.
     * </p>
     *
     * @param browser instance browser yang ingin dikembalikan; boleh {@code null}
     *                (pool akan membuat pengganti baru secara otomatis)
     */
    public void release(Browser browser) {
        if (browser == null) {
            log.warn("release() dipanggil dengan null, membuat browser pengganti");
            tryAddReplacement();
            return;
        }
        if (browser.isConnected()) {
            pool.offer(browser);
        } else {
            log.warn("Browser dikembalikan dalam keadaan terputus, mengganti...");
            replaceBrowser(browser);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Membuat satu pasangan baru (Playwright, Browser) dan mendaftarkannya ke map.
     * Playwright dibuat di sini sehingga penggunaannya terikat pada thread yang memanggil
     * method ini — aman karena dipakai sekuensial per browser.
     */
    private Browser createBrowserEntry() {
        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(launchOptions);
        playwrightMap.put(browser, playwright);
        log.debug("Browser baru dibuat dan didaftarkan ke pool");
        return browser;
    }

    /**
     * Menutup pasangan (Playwright, Browser) yang rusak lalu memasukkan
     * pasangan baru ke dalam pool.
     */
    private void replaceBrowser(Browser oldBrowser) {
        closeBrowserEntry(oldBrowser);
        tryAddReplacement();
    }

    private void tryAddReplacement() {
        try {
            Browser fresh = createBrowserEntry();
            pool.offer(fresh);
            log.info("Browser pengganti berhasil dibuat dan ditambahkan ke pool");
        } catch (Exception e) {
            log.error("Gagal membuat browser pengganti — ukuran pool berkurang sementara", e);
        }
    }

    private void closeBrowserEntry(Browser browser) {
        Playwright pw = playwrightMap.remove(browser);
        try { browser.close(); } catch (Exception ignored) {}
        if (pw != null) {
            try { pw.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Menutup semua browser dan Playwright saat aplikasi shutdown.
     * Error diabaikan agar proses shutdown tidak tersangkut.
     */
    @PreDestroy
    public void shutdown() {
        Browser b;
        while ((b = pool.poll()) != null) {
            closeBrowserEntry(b);
        }
        // Tutup Playwright yang mungkin masih terdaftar tapi browsernya tidak di antrian
        playwrightMap.forEach((browser, pw) -> {
            try { browser.close(); } catch (Exception ignored) {}
            try { pw.close(); } catch (Exception ignored) {}
        });
        playwrightMap.clear();
        log.info("Playwright browser pool dimatikan");
    }
}
