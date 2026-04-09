package org.cekpelunasan.miniapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Konfigurasi CORS untuk endpoint Mini App. Mengizinkan request dari domain Telegram,
 * domain Mini App itu sendiri, dan localhost untuk development.
 */
@Configuration
public class MiniAppCorsConfig {

    @Value("${miniapp.url:}")
    private String miniAppUrl;

    @Bean
    public CorsWebFilter miniAppCorsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("https://*.telegram.org");
        config.addAllowedOriginPattern("https://*.telegram-apps.com");
        config.addAllowedOriginPattern("http://localhost:*");
        config.addAllowedOriginPattern("http://127.0.0.1:*");

        // Izinkan origin dari domain Mini App itu sendiri
        // (browser mengirim Origin header bahkan untuk same-origin POST di balik reverse proxy)
        if (miniAppUrl != null && !miniAppUrl.isBlank()) {
            config.addAllowedOrigin(miniAppUrl);
        }

        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/mini/**", config);
        return new CorsWebFilter(source);
    }
}
