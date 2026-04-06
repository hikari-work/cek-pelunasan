package org.cekpelunasan.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;

/**
 * Menyiapkan {@link WebClient} yang sudah dikonfigurasi untuk berkomunikasi dengan gateway WhatsApp.
 * <p>
 * Gateway WhatsApp yang dipakai membutuhkan autentikasi Basic Auth (username + password
 * dikodekan ke Base64) di setiap request. Class ini mengurus pembuatan header autentikasi
 * itu sekali di awal sehingga komponen lain tinggal inject dan langsung pakai tanpa
 * harus repot atur header sendiri.
 * </p>
 * <p>
 * Jika {@code whatsapp.device.id} diisi di konfigurasi, header {@code X-Device-Id} akan
 * otomatis ditambahkan ke setiap request — berguna jika gateway mendukung multi-device.
 * </p>
 * <p>
 * Konfigurasi yang dibutuhkan:
 * <ul>
 *   <li>{@code whatsapp.gateway.url} — URL dasar gateway, misalnya {@code https://wa.example.com}</li>
 *   <li>{@code whatsapp.gateway.username} — username untuk Basic Auth</li>
 *   <li>{@code whatsapp.gateway.password} — password untuk Basic Auth</li>
 *   <li>{@code whatsapp.device.id} — (opsional) ID device WhatsApp yang dipakai</li>
 * </ul>
 * </p>
 */
@Configuration
public class WebClientConfiguration {

    @Value("${whatsapp.gateway.url}")
    private String baseUrl;

    @Value("${whatsapp.gateway.username}")
    private String username;

    @Value("${whatsapp.gateway.password}")
    private String password;

    @Value("${whatsapp.device.id:}")
    private String deviceId;

    /**
     * Membangun {@link WebClient} dengan base URL dan header autentikasi gateway WhatsApp yang sudah terpasang.
     * <p>
     * Kredensial di-encode ke Base64 sesuai skema Basic Auth (RFC 7617) dan dipasang
     * sebagai header {@code Authorization} default. Selain itu, {@code Content-Type: application/json}
     * juga sudah dipasang secara default karena semua request ke gateway ini bertipe JSON.
     * </p>
     * <p>
     * Kalau {@code deviceId} tidak kosong, header {@code X-Device-Id} juga ikut dipasang
     * sehingga gateway tahu request ini berasal dari device mana.
     * </p>
     *
     * @return {@link WebClient} yang siap dipakai untuk mengirim pesan WhatsApp
     */
    @Bean
    public WebClient whatsappWebClient() {
        String credentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + credentials)
                .defaultHeader("Content-Type", "application/json");
        if (deviceId != null && !deviceId.isBlank()) {
            builder.defaultHeader("X-Device-Id", deviceId);
        }
        return builder.build();
    }
}
