package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Main Webhook DTO yang menggabungkan semua kemungkinan
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppWebhookDTO {
	// Base fields
	@JsonProperty("sender_id")
	private String senderId;

	@JsonProperty("chat_id")
	private String chatId;

	private String from;
	private String timestamp;
	private String pushname;
	private String event;
	private String action;

	// Message content
	private MessageDTO message;

	// Media content
	private MediaDTO image;
	private MediaDTO video;
	private MediaDTO audio;
	private MediaDTO document;
	private MediaDTO sticker;


	private ReactionDTO reaction;
	private LocationDTO location;
	private ContactDTO contact;


	private ReceiptPayloadDTO payload;


	@JsonProperty("view_once")
	private Boolean viewOnce;

	private Boolean forwarded;

	@JsonProperty("revoked_chat")
	private String revokedChat;

	@JsonProperty("revoked_from_me")
	private Boolean revokedFromMe;

	@JsonProperty("revoked_message_id")
	private String revokedMessageId;

	@JsonProperty("edited_text")
	private String editedText;

	public boolean isTextMessage() {
		return message != null &&
			message.getText() != null &&
			!message.getText().trim().isEmpty() &&
			action == null;
	}

	public boolean isImageMessage() {
		return image != null;
	}

	public boolean isVideoMessage() {
		return video != null;
	}

	public boolean isAudioMessage() {
		return audio != null;
	}

	public boolean isDocumentMessage() {
		return document != null;
	}

	public boolean isStickerMessage() {
		return sticker != null;
	}

	public boolean isLocationMessage() {
		return location != null;
	}

	public boolean isContactMessage() {
		return contact != null;
	}

	public boolean isReactionMessage() {
		return reaction != null;
	}

	public boolean isReceiptEvent() {
		return "message.ack".equals(event);
	}

	public boolean isGroupEvent() {
		return "group.participants".equals(event);
	}

	public boolean isMessageRevoked() {
		return "message_revoked".equals(action);
	}

	public boolean isMessageEdited() {
		return "message_edited".equals(action);
	}

	public boolean isReplyMessage() {
		return message != null &&
			message.getRepliedId() != null &&
			!message.getRepliedId().trim().isEmpty();
	}

	public boolean isViewOnceMessage() {
		return Boolean.TRUE.equals(viewOnce);
	}

	public boolean isForwardedMessage() {
		return Boolean.TRUE.equals(forwarded);
	}

	public boolean isGroupChat() {
		return from != null && from.contains("@g.us");
	}

	public boolean isPrivateChat() {
		return chatId != null && chatId.contains("@s.whatsapp.net");
	}

	public MediaDTO getMedia() {
		if (image != null) return image;
		if (video != null) return video;
		if (audio != null) return audio;
		if (document != null) return document;
		if (sticker != null) return sticker;
		return null;
	}

	// Get media type
	public String getMediaType() {
		if (image != null) return "image";
		if (video != null) return "video";
		if (audio != null) return "audio";
		if (document != null) return "document";
		if (sticker != null) return "sticker";
		return null;
	}
	public String getCleanSenderId() {
		if (senderId == null) return null;
		return senderId.replace("@s.whatsapp.net", "");
	}

	public String getCleanChatId() {
		if (chatId == null) return null;
		return chatId.replace("@s.whatsapp.net", "").replace("@g.us", "");
	}
	public String buildChatId() {
		if (getCleanChatId() == null) {
			throw new IllegalArgumentException("Invalid WhatsApp DTO or clean chat ID");
		}

		String suffix = this.isGroupChat() ? "@g.us" : "@s.whatsapp.net";
		return this.getCleanChatId() + suffix;
	}
}
