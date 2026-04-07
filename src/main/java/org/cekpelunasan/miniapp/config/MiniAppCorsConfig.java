package org.cekpelunasan.miniapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Konfigurasi CORS untuk endpoint Mini App. Mengizinkan request dari domain Telegram
 * dan semua origin HTTP (untuk keperluan development/testing).
 */
@Configuration
public class MiniAppCorsConfig {

    @Bean
    public CorsWebFilter miniAppCorsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("https://*.telegram.org");
        config.addAllowedOriginPattern("https://*.telegram-apps.com");
        config.addAllowedOriginPattern("http://*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/mini/**", config);
        return new CorsWebFilter(source);
    }
}
