package org.cekpelunasan.platform.whatsapp.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Menyimpan informasi media yang diterima dari webhook WhatsApp.
 * <p>
 * Waktu bot menerima pesan berisi gambar, video, audio, dokumen, atau stiker,
 * detail medianya dikemas di sini: di mana file disimpan di server (path),
 * bisa diakses lewat URL apa, dan kalau ada teks pengantar dari pengirim (caption).
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaPayloadDTO {
    /** Path lokal file media di server WhatsApp gateway. */
    private String path;
    /** URL publik yang bisa dipakai untuk mengunduh file media ini. */
    private String url;
    /** Teks keterangan yang dikirim bersamaan dengan media, bisa null kalau tidak ada. */
    private String caption;
}
