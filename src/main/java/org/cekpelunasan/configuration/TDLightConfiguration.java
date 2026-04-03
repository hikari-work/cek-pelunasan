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
 * Konfigurasi TDLight client.
 * <p>
 * Menginisialisasi {@link SimpleTelegramClientFactory} dan {@link TDLibSettings}
 * yang akan digunakan oleh TelegramBot untuk membangun client via builder pattern.
 * </p>
 *
 * <p>Flow pembuatan client (Phase 2):</p>
 * <pre>
 *   factory.builder(settings)          → SimpleTelegramClientBuilder
 *   builder.addUpdateHandler(...)      → register handlers
 *   builder.build(AuthenticationSupplier.bot(token)) → SimpleTelegramClient
 * </pre>
 *
 * <p>Env vars yang dibutuhkan:</p>
 * <ul>
 *   <li>{@code TELEGRAM_API_ID} — integer dari my.telegram.org</li>
 *   <li>{@code TELEGRAM_API_HASH} — string dari my.telegram.org</li>
 *   <li>{@code TELEGRAM_BOT_TOKEN} — bot token dari @BotFather</li>
 *   <li>{@code TELEGRAM_SESSION_PATH} — path folder session TDLib (default: ./tdlight-session)</li>
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

    @Bean
    public SimpleTelegramClientFactory simpleTelegramClientFactory() {
        return new SimpleTelegramClientFactory();
    }
}
