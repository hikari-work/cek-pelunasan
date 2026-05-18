package org.cekpelunasan.controller;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.platform.whatsapp.service.Routers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Menerima pesan masuk dari gateway WhatsApp melalui mekanisme webhook.
 * <p>
 * Saat ada pesan WhatsApp yang masuk ke nomor yang terdaftar, gateway akan mengirimkan
 * HTTP POST ke endpoint ini dengan payload berisi detail pesan. Controller langsung
 * meneruskan payload tersebut ke {@link Routers} untuk diproses lebih lanjut, lalu
 * segera membalas gateway dengan {@code 200 OK}.
 * </p>
 * <p>
 * Kenapa langsung balas {@code 200 OK} tanpa menunggu proses selesai? Karena gateway
 * WhatsApp punya timeout yang ketat. Kalau response terlambat, gateway akan menganggap
 * pengiriman gagal dan mencoba kirim ulang. Pemrosesan asynchronous lewat {@link Routers}
 * memastikan response selalu cepat meski proses di belakangnya butuh waktu.
 * </p>
 */
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final Routers routers;

    /**
     * Menerima dan memproses payload webhook dari gateway WhatsApp versi 2.
     * <p>
     * Payload di-parse otomatis oleh Spring menjadi {@link WhatsAppWebhookDTO},
     * lalu diteruskan ke {@link Routers#handle(WhatsAppWebhookDTO)} yang akan
     * menentukan handler mana yang tepat berdasarkan jenis pesannya.
     * </p>
     * <p>
     * Response selalu {@code 200 OK} dengan body {@code "OK"} — gateway membutuhkan
     * ini sebagai tanda bahwa webhook sudah diterima dengan baik.
     * </p>
     *
     * @param dto payload webhook yang dikirim gateway, berisi informasi pengirim, tipe pesan,
     *            dan isi pesan WhatsApp
     * @return {@code Mono<ResponseEntity>} berisi status {@code 200 OK}
     */
    @PostMapping("/v2/whatsapp")
    public Mono<ResponseEntity<String>> whatsappV2(@RequestBody WhatsAppWebhookDTO dto) {
        routers.handle(dto);
        return Mono.just(ResponseEntity.ok("OK"));
    }
}
