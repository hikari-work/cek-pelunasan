package org.cekpelunasan.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

/**
 * Menyiapkan [WebClient] yang sudah dikonfigurasi untuk berkomunikasi dengan gateway WhatsApp.
 * 
 * 
 * Gateway WhatsApp yang dipakai membutuhkan autentikasi Basic Auth (username + password
 * dikodekan ke Base64) di setiap request. Class ini mengurus pembuatan header autentikasi
 * itu sekali di awal sehingga komponen lain tinggal inject dan langsung pakai tanpa
 * harus repot atur header sendiri.
 * 
 * 
 * 
 * Jika `whatsapp.device.id` diisi di konfigurasi, header `X-Device-Id` akan
 * otomatis ditambahkan ke setiap request — berguna jika gateway mendukung multi-device.
 * 
 * 
 * 
 * Konfigurasi yang dibutuhkan:
 * 
 *  * `whatsapp.gateway.url` — URL dasar gateway, misalnya `https://wa.example.com`
 *  * `whatsapp.gateway.username` — username untuk Basic Auth
 *  * `whatsapp.gateway.password` — password untuk Basic Auth
 *  * `whatsapp.device.id` — (opsional) ID device WhatsApp yang dipakai
 * 
 * 
 */
@Configuration
class WebClientConfiguration {
    @Value("\${whatsapp.gateway.url}")
    private val baseUrl: String? = null

    @Value("\${whatsapp.gateway.username}")
    private val username: String? = null

    @Value("\${whatsapp.gateway.password}")
    private val password: String? = null

    @Value("\${whatsapp.device.id:}")
    private val deviceId: String? = null

    /**
     * Membangun [WebClient] dengan base URL dan header autentikasi gateway WhatsApp yang sudah terpasang.
     * 
     * 
     * Kredensial di-encode ke Base64 sesuai skema Basic Auth (RFC 7617) dan dipasang
     * sebagai header `Authorization` default. Selain itu, `Content-Type: application/json`
     * juga sudah dipasang secara default karena semua request ke gateway ini bertipe JSON.
     * 
     * 
     * 
     * Kalau `deviceId` tidak kosong, header `X-Device-Id` juga ikut dipasang
     * sehingga gateway tahu request ini berasal dari device mana.
     * 
     * 
     * @return [WebClient] yang siap dipakai untuk mengirim pesan WhatsApp
     */
    @Bean
    fun whatsappWebClient(): WebClient {
        val credentials = Base64.getEncoder()
            .encodeToString((username + ":" + password).toByteArray())
        val builder = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Basic " + credentials)
            .defaultHeader("Content-Type", "application/json")
        if (deviceId != null && !deviceId.isBlank()) {
            builder.defaultHeader("X-Device-Id", deviceId)
        }
        return builder.build()
    }
}
