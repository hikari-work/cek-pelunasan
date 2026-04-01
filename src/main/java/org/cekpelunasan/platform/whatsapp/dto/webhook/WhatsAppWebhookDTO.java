package org.cekpelunasan.platform.whatsapp.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    public String getFrom() {
        return payload != null ? payload.getFrom() : null;
    }

    public String getCleanChatId() {
        return payload != null ? payload.getCleanChatId() : null;
    }

    public String getCleanSenderId() {
        return payload != null ? payload.getCleanFrom() : null;
    }

    public boolean isGroupChat() {
        return payload != null && payload.isGroupChat();
    }

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

    public boolean isTextMessage() {
        return "message".equals(event) && payload != null
                && payload.getBody() != null && !payload.getBody().trim().isEmpty();
    }

    public boolean isReactionMessage() {
        return "message.reaction".equals(event);
    }

    public boolean isMessageRevoked() {
        return "message.revoked".equals(event);
    }

    public boolean isMessageEdited() {
        return "message.edited".equals(event);
    }

    public boolean isReceiptEvent() {
        return "message.ack".equals(event);
    }

    public boolean isGroupEvent() {
        return "group.participants".equals(event);
    }

    public boolean isReplyMessage() {
        return "message".equals(event) && payload != null
                && payload.getRepliedToId() != null && !payload.getRepliedToId().trim().isEmpty();
    }

    public boolean isImageMessage() {
        return "message".equals(event) && payload != null && payload.getImage() != null;
    }

    public boolean isVideoMessage() {
        return "message".equals(event) && payload != null && payload.getVideo() != null;
    }

    public boolean isAudioMessage() {
        return "message".equals(event) && payload != null && payload.getAudio() != null;
    }

    public boolean isDocumentMessage() {
        return "message".equals(event) && payload != null && payload.getDocument() != null;
    }

    public boolean isStickerMessage() {
        return "message".equals(event) && payload != null && payload.getSticker() != null;
    }

    public boolean isPrivateChat() {
        return payload != null && payload.getChatId() != null
                && payload.getChatId().contains("@s.whatsapp.net");
    }

    public MediaPayloadDTO getMedia() {
        if (payload == null) return null;
        if (payload.getImage() != null) return payload.getImage();
        if (payload.getVideo() != null) return payload.getVideo();
        if (payload.getAudio() != null) return payload.getAudio();
        if (payload.getDocument() != null) return payload.getDocument();
        if (payload.getSticker() != null) return payload.getSticker();
        return null;
    }

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
