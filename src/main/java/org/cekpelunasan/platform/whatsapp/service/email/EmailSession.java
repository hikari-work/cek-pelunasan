package org.cekpelunasan.platform.whatsapp.service.email;

import java.util.ArrayList;
import java.util.List;

/**
 * Menyimpan state sesi email milik satu pengirim WhatsApp.
 * Dibuat saat user ketik .email, diisi media sampai .done atau timeout 60 detik.
 */
public class EmailSession {

    private final String chatId;
    private final String senderPhone;
    private final String fromName;
    private final List<CollectedMedia> mediaList = new ArrayList<>();

    public EmailSession(String chatId, String senderPhone, String fromName) {
        this.chatId = chatId;
        this.senderPhone = senderPhone;
        this.fromName = fromName;
    }

    public String getChatId() { return chatId; }
    public String getSenderPhone() { return senderPhone; }
    public String getFromName() { return fromName; }
    public List<CollectedMedia> getMediaList() { return mediaList; }

    public void addMedia(CollectedMedia media) {
        mediaList.add(media);
    }

    /**
     * Satu file media yang sudah terkumpul dalam sesi ini.
     *
     * @param downloadUrl URL lengkap untuk download file dari gateway
     * @param filename    nama file untuk attachment email
     * @param mediaType   tipe media: "image", "video", "audio", "document"
     * @param caption     keterangan dari pengirim, bisa null
     */
    public record CollectedMedia(String downloadUrl, String filename, String mediaType, String caption) {}
}
