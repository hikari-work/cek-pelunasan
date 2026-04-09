package org.cekpelunasan.configuration

import it.tdlight.Init
import it.tdlight.Log
import it.tdlight.Slf4JLogMessageHandler
import it.tdlight.client.APIToken
import it.tdlight.client.SimpleTelegramClientFactory
import it.tdlight.client.TDLibSettings
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

/**
 * Menyiapkan semua yang dibutuhkan TDLight agar bot Telegram bisa terhubung ke API Telegram.
 * 
 * 
 * TDLight adalah library berbasis TDLib (library resmi Telegram), dan sebelum bisa dipakai
 * kita perlu memuat native library-nya, mengatur log, lalu memberi tahu TDLib di mana
 * menyimpan data sesi dan file yang diunduh.
 * 
 * 
 * 
 * Bean yang dihasilkan class ini ([TDLibSettings] dan [SimpleTelegramClientFactory])
 * dipakai oleh komponen bot untuk membangun koneksi ke Telegram via builder pattern:
 * 
 * <pre>
 * factory.builder(settings)
 * .addUpdateHandler(...)
 * .build(AuthenticationSupplier.bot(token))
</pre> * 
 * 
 * 
 * Environment variable yang wajib diisi:
 * 
 *  * `TELEGRAM_API_ID` — integer dari my.telegram.org
 *  * `TELEGRAM_API_HASH` — string dari my.telegram.org
 *  * `TELEGRAM_SESSION_PATH` — folder penyimpanan sesi TDLib (default: `./tdlight-session`)
 * 
 */
@Configuration
class TDLightConfiguration {
    @Value("\${telegram.api.id}")
    private val apiId = 0

    @Value("\${telegram.api.hash}")
    private val apiHash: String? = null

    @Value("\${telegram.session.path:./tdlight-session}")
    private val sessionPath: String? = null

    /**
     * Memuat native library TDLight, mengatur log, lalu membangun objek [TDLibSettings]
     * yang berisi API token dan path folder sesi.
     * 
     * 
     * Method ini hanya dijalankan sekali saat aplikasi start. Jika native library gagal dimuat
     * (misalnya file .so/.dll tidak ada), exception akan langsung dilempar dan aplikasi tidak
     * akan bisa berjalan.
     * 
     * 
     * 
     * Dua sub-folder akan dipakai di dalam `sessionPath`:
     * 
     *  * `/data` — menyimpan data sesi dan database TDLib
     *  * `/downloads` — menyimpan file yang diunduh melalui Telegram
     * 
     * 
     * 
     * @return [TDLibSettings] yang siap dipakai untuk membangun Telegram client
     * @throws Exception jika inisialisasi native library TDLight gagal
     */
    @Bean
    @Throws(Exception::class)
    fun tdLibSettings(): TDLibSettings {
        log.info("Initializing TDLight natives...")
        Init.init()
        Log.setLogMessageHandler(1, Slf4JLogMessageHandler())
        log.info("TDLight natives loaded successfully.")

        val apiToken = APIToken(apiId, apiHash)
        val settings = TDLibSettings.create(apiToken)

        val path = Path.of(sessionPath)
        settings.setDatabaseDirectoryPath(path.resolve("data"))
        settings.setDownloadedFilesDirectoryPath(path.resolve("downloads"))

        log.info("TDLight session path: {}", path.toAbsolutePath())
        return settings
    }

    /**
     * Membuat [SimpleTelegramClientFactory] yang nantinya dipakai untuk membangun
     * instance Telegram client dengan pengaturan dan handler masing-masing.
     * 
     * 
     * Factory ini stateless — komponen lain tinggal inject dan panggil `builder()`
     * setiap kali perlu membuat client baru.
     * 
     * 
     * @return factory untuk membangun Telegram client
     */
    @Bean
    fun simpleTelegramClientFactory(): SimpleTelegramClientFactory {
        return SimpleTelegramClientFactory()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TDLightConfiguration::class.java)
    }
}
