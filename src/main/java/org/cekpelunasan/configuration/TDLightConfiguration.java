package org.cekpelunasan.configuration;

import it.tdlight.Init;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.APIToken;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Menyiapkan semua yang dibutuhkan TDLight agar bot Telegram bisa terhubung ke API Telegram.
 * <p>
 * TDLight adalah library berbasis TDLib (library resmi Telegram), dan sebelum bisa dipakai
 * kita perlu memuat native library-nya, mengatur log, lalu memberi tahu TDLib di mana
 * menyimpan data sesi dan file yang diunduh.
 * </p>
 * <p>
 * Bean yang dihasilkan class ini ({@link TDLibSettings} dan {@link SimpleTelegramClientFactory})
 * dipakai oleh komponen bot untuk membangun koneksi ke Telegram via builder pattern:
 * </p>
 * <pre>
 *   factory.builder(settings)
 *     .addUpdateHandler(...)
 *     .build(AuthenticationSupplier.bot(token))
 * </pre>
 *
 * <p>Environment variable yang wajib diisi:</p>
 * <ul>
 *   <li>{@code TELEGRAM_API_ID} — integer dari my.telegram.org</li>
 *   <li>{@code TELEGRAM_API_HASH} — string dari my.telegram.org</li>
 *   <li>{@code TELEGRAM_SESSION_PATH} — folder penyimpanan sesi TDLib (default: {@code ./tdlight-session})</li>
 * </ul>
 */
@Configuration
public class TDLightConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TDLightConfiguration.class);

    @Value("${telegram.api.id}")
    private int apiId;

    @Value("${telegram.api.hash}")
    private String apiHash;

    @Value("${telegram.session.path:./tdlight-session}")
    private String sessionPath;

    /**
     * Memuat native library TDLight, mengatur log, lalu membangun objek {@link TDLibSettings}
     * yang berisi API token dan path folder sesi.
     * <p>
     * Method ini hanya dijalankan sekali saat aplikasi start. Jika native library gagal dimuat
     * (misalnya file .so/.dll tidak ada), exception akan langsung dilempar dan aplikasi tidak
     * akan bisa berjalan.
     * </p>
     * <p>
     * Dua sub-folder akan dipakai di dalam {@code sessionPath}:
     * <ul>
     *   <li>{@code /data} — menyimpan data sesi dan database TDLib</li>
     *   <li>{@code /downloads} — menyimpan file yang diunduh melalui Telegram</li>
     * </ul>
     * </p>
     *
     * @return {@link TDLibSettings} yang siap dipakai untuk membangun Telegram client
     * @throws Exception jika inisialisasi native library TDLight gagal
     */
    @Bean
    public TDLibSettings tdLibSettings() throws Exception {
        log.info("Initializing TDLight natives...");
        Init.init();
        Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
        log.info("TDLight natives loaded successfully.");

        APIToken apiToken = new APIToken(apiId, apiHash);
        TDLibSettings settings = TDLibSettings.create(apiToken);

        Path path = Path.of(sessionPath);
        settings.setDatabaseDirectoryPath(path.resolve("data"));
        settings.setDownloadedFilesDirectoryPath(path.resolve("downloads"));

        log.info("TDLight session path: {}", path.toAbsolutePath());
        return settings;
    }

    /**
     * Membuat {@link SimpleTelegramClientFactory} yang nantinya dipakai untuk membangun
     * instance Telegram client dengan pengaturan dan handler masing-masing.
     * <p>
     * Factory ini stateless — komponen lain tinggal inject dan panggil {@code builder()}
     * setiap kali perlu membuat client baru.
     * </p>
     *
     * @return factory untuk membangun Telegram client
     */
    @Bean
    public SimpleTelegramClientFactory simpleTelegramClientFactory() {
        return new SimpleTelegramClientFactory();
    }
}
