package org.cekpelunasan.platform.whatsapp.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pintu masuk utama untuk semua pesan dan event yang dikirim WhatsApp gateway ke bot kita.
 * <p>
 * Setiap kali ada aktivitas di WhatsApp — pesan masuk, reaksi, pesan diedit,
 * event grup, atau panggilan — gateway akan mengirim data dalam format ini ke endpoint webhook.
 * Field {@code event} membedakan jenis aktivitasnya, sementara {@code payload}
 * menyimpan detail lengkap dari aktivitas tersebut.
 * </p>
 * <p>
 * Class ini juga menyediakan banyak helper method supaya service tidak perlu
 * mengakses payload secara langsung. Misalnya {@link #isTextMessage()} untuk cek
 * apakah ini pesan teks, atau {@link #buildChatId()} untuk membangun chat ID
 * yang siap dipakai saat mengirim balasan (termasuk menangani kasus akun LID).
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppWebhookDTO {

    private String event;

    @JsonProperty("device_id")
    private String deviceId;

    private String timestamp;

    private MessagePayloadDTO payload;

    // --- Delegate helpers (backward compat untuk semua service) ---

    /** Mengembalikan alamat lengkap pengirim termasuk suffix WhatsApp. */
    public String getFrom() {
        return payload != null ? payload.getFrom() : null;
    }

    /** Mengembalikan chat ID tanpa suffix WhatsApp (@s.whatsapp.net atau @g.us). */
    public String getCleanChatId() {
        return payload != null ? payload.getCleanChatId() : null;
    }

    /** Mengembalikan nomor pengirim yang sudah bersih tanpa suffix WhatsApp. */
    public String getCleanSenderId() {
        return payload != null ? payload.getCleanFrom() : null;
    }

    /**
     * Mengecek apakah pesan ini berasal dari chat grup.
     *
     * @return {@code true} kalau event ini terjadi di grup WhatsApp
     */
    public boolean isGroupChat() {
        return payload != null && payload.isGroupChat();
    }

    /**
     * Membangun chat ID lengkap yang siap dipakai untuk mengirim pesan balasan.
     * <p>
     * Method ini menangani tiga skenario berbeda:
     * <ol>
     *   <li>Pengirim menggunakan akun LID (format baru WhatsApp) — pakai suffix "@lid"</li>
     *   <li>Pengirim bukan dari grup dan punya fromLid — pakai "@lid"</li>
     *   <li>Pengirim normal — pakai "@s.whatsapp.net" untuk personal atau "@g.us" untuk grup</li>
     * </ol>
     * </p>
     *
     * @return chat ID lengkap yang siap dipakai sebagai tujuan pengiriman pesan
     * @throws IllegalArgumentException kalau payload null atau chat ID tidak valid
     */
    public String buildChatId() {
        if (payload == null) throw new IllegalArgumentException("Payload is null");
        String from = payload.getFrom();
        String chatId = payload.getChatId();
        String fromLid = payload.getFromLid();

        if (from != null && from.contains("@lid")) {
            return chatId + "@lid";
        }
        if (fromLid != null && from != null && !from.contains("@g.us")) {
            return from + "@lid";
        }
        if (payload.getCleanChatId() == null) {
            throw new IllegalArgumentException("Invalid WhatsApp DTO or clean chat ID");
        }
        String suffix = payload.isGroupChat() ? "@g.us" : "@s.whatsapp.net";
        return payload.getCleanChatId() + suffix;
    }

    // --- Event helpers ---

    /** Mengecek apakah ini pesan teks biasa yang isinya tidak kosong. */
    public boolean isTextMessage() {
        return "message".equals(event) && payload != null
                && payload.getBody() != null && !payload.getBody().trim().isEmpty();
    }

    /** Mengecek apakah event ini adalah reaksi emoji ke sebuah pesan. */
    public boolean isReactionMessage() {
        return "message.reaction".equals(event);
    }

    /** Mengecek apakah event ini adalah notifikasi pesan yang ditarik kembali. */
    public boolean isMessageRevoked() {
        return "message.revoked".equals(event);
    }

    /** Mengecek apakah event ini adalah notifikasi pesan yang diedit. */
    public boolean isMessageEdited() {
        return "message.edited".equals(event);
    }

    /** Mengecek apakah event ini adalah tanda terima (delivered/read) dari pesan yang dikirim. */
    public boolean isReceiptEvent() {
        return "message.ack".equals(event);
    }

    /** Mengecek apakah event ini adalah perubahan anggota grup (masuk/keluar). */
    public boolean isGroupEvent() {
        return "group.participants".equals(event);
    }

    /** Mengecek apakah ini pesan balasan (quote) dari pesan lain. */
    public boolean isReplyMessage() {
        return "message".equals(event) && payload != null
                && payload.getRepliedToId() != null && !payload.getRepliedToId().trim().isEmpty();
    }

    /** Mengecek apakah pesan ini berisi gambar. */
    public boolean isImageMessage() {
        return "message".equals(event) && payload != null && payload.getImage() != null;
    }

    /** Mengecek apakah pesan ini berisi video. */
    public boolean isVideoMessage() {
        return "message".equals(event) && payload != null && payload.getVideo() != null;
    }

    /** Mengecek apakah pesan ini berisi audio. */
    public boolean isAudioMessage() {
        return "message".equals(event) && payload != null && payload.getAudio() != null;
    }

    /** Mengecek apakah pesan ini berisi dokumen/file. */
    public boolean isDocumentMessage() {
        return "message".equals(event) && payload != null && payload.getDocument() != null;
    }

    /** Mengecek apakah pesan ini berisi stiker. */
    public boolean isStickerMessage() {
        return "message".equals(event) && payload != null && payload.getSticker() != null;
    }

    /** Mengecek apakah pesan ini berasal dari chat personal (bukan grup). */
    public boolean isPrivateChat() {
        return payload != null && payload.getChatId() != null
                && payload.getChatId().contains("@s.whatsapp.net");
    }

    /**
     * Mengembalikan media yang ada dalam pesan ini, dengan urutan prioritas:
     * gambar, video, audio, dokumen, stiker.
     *
     * @return detail media jika ada, atau {@code null} kalau tidak ada media
     */
    public MediaPayloadDTO getMedia() {
        if (payload == null) return null;
        if (payload.getImage() != null) return payload.getImage();
        if (payload.getVideo() != null) return payload.getVideo();
        if (payload.getAudio() != null) return payload.getAudio();
        if (payload.getDocument() != null) return payload.getDocument();
        if (payload.getSticker() != null) return payload.getSticker();
        return null;
    }

    /**
     * Mengembalikan tipe media dalam pesan ini sebagai string ("image", "video", dll.).
     *
     * @return nama tipe media, atau {@code null} kalau tidak ada media
     */
    public String getMediaType() {
        if (payload == null) return null;
        if (payload.getImage() != null) return "image";
        if (payload.getVideo() != null) return "video";
        if (payload.getAudio() != null) return "audio";
        if (payload.getDocument() != null) return "document";
        if (payload.getSticker() != null) return "sticker";
        return null;
    }
}
