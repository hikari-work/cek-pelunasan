package org.cekpelunasan.platform.whatsapp.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Representasi isi (payload) dari setiap pesan yang masuk ke webhook WhatsApp.
 * <p>
 * Satu class ini merangkum semua jenis pesan yang mungkin diterima: pesan teks biasa,
 * media (gambar, video, audio, dokumen, stiker), reaksi emoji, tanda terima (ack),
 * event grup, kehadiran chat, hingga notifikasi panggilan masuk.
 * Tidak semua field akan terisi sekaligus — tergantung jenis event yang datang.
 * </p>
 * <p>
 * Untuk kemudahan, ada helper method seperti {@link #isGroupChat()},
 * {@link #getCleanFrom()}, dan {@link #getCleanChatId()} supaya kode di service
 * tidak perlu repot-repot membersihkan suffix WhatsApp (@s.whatsapp.net, @g.us) sendiri.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessagePayloadDTO {

    // Common message fields
    private String id;
    @JsonProperty("chat_id")
    private String chatId;
    private String from;
    @JsonProperty("from_lid")
    private String fromLid;
    @JsonProperty("from_name")
    private String fromName;
    private String timestamp;
    @JsonProperty("is_from_me")
    private Boolean isFromMe;

    // Text
    private String body;
    @JsonProperty("replied_to_id")
    private String repliedToId;
    @JsonProperty("quoted_body")
    private String quotedBody;

    // Media
    @JsonDeserialize(using = MediaPayloadDeserializer.class)
    private MediaPayloadDTO image;
    @JsonDeserialize(using = MediaPayloadDeserializer.class)
    private MediaPayloadDTO video;
    @JsonDeserialize(using = MediaPayloadDeserializer.class)
    private MediaPayloadDTO audio;
    @JsonDeserialize(using = MediaPayloadDeserializer.class)
    private MediaPayloadDTO document;
    @JsonDeserialize(using = MediaPayloadDeserializer.class)
    private MediaPayloadDTO sticker;

    // Reaction (message.reaction)
    private String reaction;
    @JsonProperty("reacted_message_id")
    private String reactedMessageId;

    // Receipt (message.ack)
    private List<String> ids;
    @JsonProperty("receipt_type")
    private String receiptType;
    @JsonProperty("receipt_type_description")
    private String receiptTypeDescription;

    // Group participants (group.participants)
    private String type;
    private List<String> jids;

    // Chat presence (chat_presence)
    private String state;
    private String media;
    @JsonProperty("is_group")
    private Boolean isGroup;

    // Call (call.offer)
    @JsonProperty("call_id")
    private String callId;
    @JsonProperty("auto_rejected")
    private Boolean autoRejected;
    @JsonProperty("remote_platform")
    private String remotePlatform;
    @JsonProperty("remote_version")
    private String remoteVersion;
    @JsonProperty("group_jid")
    private String groupJid;

    /**
     * Mengecek apakah pesan ini berasal dari chat grup.
     * Cara bacanya sederhana: kalau chat ID-nya berakhiran "@g.us", berarti itu grup.
     *
     * @return {@code true} kalau pesan dari grup WhatsApp
     */
    public boolean isGroupChat() {
        return chatId != null && chatId.contains("@g.us");
    }

    /**
     * Mengembalikan nomor pengirim tanpa suffix WhatsApp.
     * Misalnya "6281234567890@s.whatsapp.net" jadi "6281234567890".
     *
     * @return nomor pengirim yang sudah bersih, atau {@code null} kalau tidak ada
     */
    public String getCleanFrom() {
        if (from == null) return null;
        return from.replace("@s.whatsapp.net", "").replace("@g.us", "");
    }

    /**
     * Mengembalikan chat ID tanpa suffix WhatsApp.
     * Berguna saat kita hanya butuh angka/identifiernya saja untuk diproses lebih lanjut.
     *
     * @return chat ID yang sudah bersih, atau {@code null} kalau tidak ada
     */
    public String getCleanChatId() {
        if (chatId == null) return null;
        return chatId.replace("@s.whatsapp.net", "").replace("@g.us", "");
    }
}
