package org.cekpelunasan.platform.whatsapp.dto.webhook;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Deserializer khusus untuk mengurai field media dari payload webhook WhatsApp.
 * <p>
 * Masalahnya, API WhatsApp kadang mengirim field media (gambar, video, dll.)
 * hanya sebagai string path, tapi kadang juga sebagai objek JSON lengkap dengan
 * path, URL, dan caption. Deserializer ini menangani kedua format itu
 * supaya kode di service tidak perlu khawatir soal perbedaan formatnya.
 * </p>
 */
public class MediaPayloadDeserializer extends StdDeserializer<MediaPayloadDTO> {

    public MediaPayloadDeserializer() {
        super(MediaPayloadDTO.class);
    }

    /**
     * Mengurai JSON menjadi {@link MediaPayloadDTO}.
     * <p>
     * Kalau JSON-nya cuma string (path saja), langsung set ke field path.
     * Kalau JSON-nya objek, ambil field path, url, dan caption satu per satu.
     * </p>
     *
     * @param p    parser JSON yang sedang aktif
     * @param ctxt konteks deserialisasi dari Jackson
     * @return objek {@link MediaPayloadDTO} yang sudah terisi datanya
     */
    @Override
    public MediaPayloadDTO deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node.isTextual()) {
            MediaPayloadDTO dto = new MediaPayloadDTO();
            dto.setPath(node.asText());
            return dto;
        }
        MediaPayloadDTO dto = new MediaPayloadDTO();
        if (node.has("path")) dto.setPath(node.get("path").asText());
        if (node.has("url")) dto.setUrl(node.get("url").asText());
        if (node.has("caption")) dto.setCaption(node.get("caption").asText());
        return dto;
    }
}
