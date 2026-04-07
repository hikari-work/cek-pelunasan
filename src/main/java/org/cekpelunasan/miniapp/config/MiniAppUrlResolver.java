package org.cekpelunasan.miniapp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Menentukan URL publik Mini App secara otomatis saat startup.
 *
 * <p>Jika {@code MINIAPP_URL} di-set via environment variable, URL tersebut langsung
 * digunakan (cocok untuk production dengan domain + HTTPS). Jika tidak, URL di-resolve
 * otomatis dari IP publik server (via ipinfo.io dengan fallback ke ipify.org) dikombinasikan
 * dengan port yang dikonfigurasi di {@code server.port}.</p>
 *
 * <p><b>Catatan production:</b> Telegram Mini App wajib menggunakan HTTPS.
 * Set {@code MINIAPP_URL=https://your-domain.com} di environment production.</p>
 */
@Component
public class MiniAppUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(MiniAppUrlResolver.class);

    @Value("${miniapp.url:}")
    private String configuredUrl;

    @Value("${server.port:8080}")
    private int port;

    private String resolvedUrl;

    @EventListener(ApplicationReadyEvent.class)
    public void resolve() {
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            resolvedUrl = configuredUrl.stripTrailing().replaceAll("/$", "");
            log.info("Mini App URL (configured): {}", resolvedUrl);
            return;
        }

        String ip = fetchPublicIp("https://ipinfo.io/ip");
        if (ip == null) {
            log.warn("ipinfo.io gagal, mencoba ipify.org...");
            ip = fetchPublicIp("https://api.ipify.org");
        }

        if (ip != null) {
            resolvedUrl = "http://" + ip.strip() + ":" + port;
            log.info("Mini App URL (auto-detected): {}", resolvedUrl);
        } else {
            resolvedUrl = "http://localhost:" + port;
            log.warn("Gagal auto-detect IP publik. Gunakan MINIAPP_URL untuk production. Sementara: {}", resolvedUrl);
        }
    }

    private String fetchPublicIp(String url) {
        try {
            return WebClient.create()
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (Exception e) {
            log.debug("Gagal fetch IP dari {}: {}", url, e.getMessage());
            return null;
        }
    }

    public String getUrl() {
        return resolvedUrl;
    }
}
