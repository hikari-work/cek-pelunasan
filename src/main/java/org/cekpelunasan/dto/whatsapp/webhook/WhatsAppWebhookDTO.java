package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Main Webhook DTO that aggregates all possible webhook event structures.
 * <p>
 * This class serves as a monolithic DTO to handle incoming webhook payloads,
 * containing fields for all supported message types and events.
 * It provides utility methods to determine the type of message or event.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppWebhookDTO {
	// Base fields
	/**
	 * The ID of the sender.
	 */
	@JsonProperty("sender_id")
	private String senderId;

	/**
	 * The ID of the chat.
	 */
	@JsonProperty("chat_id")
	private String chatId;

	/**
	 * The LID of the sender (if applicable).
	 */
	@JsonProperty("from_lid")
	private String fromLid;

	/**
	 * The sender's phone number.
	 */
	private String from;
	/**
	 * The timestamp of the event.
	 */
	private String timestamp;
	/**
	 * The pushname of the sender.
	 */
	private String pushname;
	/**
	 * The type of event.
	 */
	private String event;
	/**
	 * The action performed.
	 */
	private String action;

	// Message content
	/**
	 * Text message content.
	 */
	private MessageDTO message;

	// Media content
	/**
	 * Image media content.
	 */
	private MediaDTO image;
	/**
	 * Video media content.
	 */
	private MediaDTO video;
	/**
	 * Audio media content.
	 */
	private MediaDTO audio;
	/**
	 * Document media content.
	 */
	private MediaDTO document;
	/**
	 * Sticker media content.
	 */
	private MediaDTO sticker;

	/**
	 * Reaction content.
	 */
	private ReactionDTO reaction;
	/**
	 * Location content.
	 */
	private LocationDTO location;
	/**
	 * Contact content.
	 */
	private ContactDTO contact;

	/**
	 * Receipt payload.
	 */
	private ReceiptPayloadDTO payload;

	/**
	 * Indicates if the message is view once.
	 */
	@JsonProperty("view_once")
	private Boolean viewOnce;

	/**
	 * Indicates if the message was forwarded.
	 */
	private Boolean forwarded;

	/**
	 * The ID of the chat where a message was revoked.
	 */
	@JsonProperty("revoked_chat")
	private String revokedChat;

	/**
	 * Indicates if the revoked message was sent by the current user.
	 */
	@JsonProperty("revoked_from_me")
	private Boolean revokedFromMe;

	/**
	 * The unique ID of the revoked message.
	 */
	@JsonProperty("revoked_message_id")
	private String revokedMessageId;

	@JsonProperty("edited_text")
	private String editedText;

	/**
	 * Checks if the message is a text message.
	 * 
	 * @return true if it is a text message, false otherwise.
	 */
	public boolean isTextMessage() {
		return message != null &&
				message.getText() != null &&
				!message.getText().trim().isEmpty() &&
				action == null;
	}

	/**
	 * Checks if the message is an image message.
	 * 
	 * @return true if it is an image message, false otherwise.
	 */
	public boolean isImageMessage() {
		return image != null;
	}

	/**
	 * Checks if the message is a video message.
	 * 
	 * @return true if it is a video message, false otherwise.
	 */
	public boolean isVideoMessage() {
		return video != null;
	}

	/**
	 * Checks if the message is an audio message.
	 * 
	 * @return true if it is an audio message, false otherwise.
	 */
	public boolean isAudioMessage() {
		return audio != null;
	}

	/**
	 * Checks if the message is a document message.
	 * 
	 * @return true if it is a document message, false otherwise.
	 */
	public boolean isDocumentMessage() {
		return document != null;
	}

	/**
	 * Checks if the message is a sticker message.
	 * 
	 * @return true if it is a sticker message, false otherwise.
	 */
	public boolean isStickerMessage() {
		return sticker != null;
	}

	/**
	 * Checks if the message is a location message.
	 * 
	 * @return true if it is a location message, false otherwise.
	 */
	public boolean isLocationMessage() {
		return location != null;
	}

	/**
	 * Checks if the message is a contact message.
	 * 
	 * @return true if it is a contact message, false otherwise.
	 */
	public boolean isContactMessage() {
		return contact != null;
	}

	/**
	 * Checks if the message is a reaction.
	 * 
	 * @return true if it is a reaction, false otherwise.
	 */
	public boolean isReactionMessage() {
		return reaction != null;
	}

	/**
	 * Checks if the event is a receipt event.
	 * 
	 * @return true if it is a receipt event, false otherwise.
	 */
	public boolean isReceiptEvent() {
		return "message.ack".equals(event);
	}

	/**
	 * Checks if the event is a group event.
	 * 
	 * @return true if it is a group event, false otherwise.
	 */
	public boolean isGroupEvent() {
		return "group.participants".equals(event);
	}

	/**
	 * Checks if the message was revoked.
	 * 
	 * @return true if the message was revoked, false otherwise.
	 */
	public boolean isMessageRevoked() {
		return "message_revoked".equals(action);
	}

	/**
	 * Checks if the message was edited.
	 * 
	 * @return true if the message was edited, false otherwise.
	 */
	public boolean isMessageEdited() {
		return "message_edited".equals(action);
	}

	/**
	 * Checks if the message is a reply to another message.
	 * 
	 * @return true if it is a reply, false otherwise.
	 */
	public boolean isReplyMessage() {
		return message != null &&
				message.getRepliedId() != null &&
				!message.getRepliedId().trim().isEmpty();
	}

	/**
	 * Checks if the message is set to view once.
	 * 
	 * @return true if it is view once, false otherwise.
	 */
	public boolean isViewOnceMessage() {
		return Boolean.TRUE.equals(viewOnce);
	}

	/**
	 * Checks if the message was forwarded.
	 * 
	 * @return true if forwarded, false otherwise.
	 */
	public boolean isForwardedMessage() {
		return Boolean.TRUE.equals(forwarded);
	}

	/**
	 * Checks if the chat is a group chat.
	 * 
	 * @return true if it is a group chat, false otherwise.
	 */
	public boolean isGroupChat() {
		return from != null && from.contains("@g.us");
	}

	/**
	 * Checks if the chat is a private chat.
	 * 
	 * @return true if it is a private chat, false otherwise.
	 */
	public boolean isPrivateChat() {
		return chatId != null && chatId.contains("@s.whatsapp.net");
	}

	/**
	 * Gets the media object from the message if present.
	 * 
	 * @return the MediaDTO if found, null otherwise.
	 */
	public MediaDTO getMedia() {
		if (image != null)
			return image;
		if (video != null)
			return video;
		if (audio != null)
			return audio;
		if (document != null)
			return document;
		if (sticker != null)
			return sticker;
		return null;
	}

	// Get media type
	/**
	 * Gets the type of the media as a string.
	 * 
	 * @return the media type string (image, video, audio, document, sticker), or
	 *         null if no media.
	 */
	public String getMediaType() {
		if (image != null)
			return "image";
		if (video != null)
			return "video";
		if (audio != null)
			return "audio";
		if (document != null)
			return "document";
		if (sticker != null)
			return "sticker";
		return null;
	}

	/**
	 * Returns the sender ID without the domain suffix.
	 * 
	 * @return the clean sender ID.
	 */
	public String getCleanSenderId() {
		if (senderId == null)
			return null;
		return senderId.replace("@s.whatsapp.net", "");
	}

	/**
	 * Returns the chat ID without domain suffixes.
	 * 
	 * @return the clean chat ID.
	 */
	public String getCleanChatId() {
		if (chatId == null)
			return null;
		return chatId.replace("@s.whatsapp.net", "").replace("@g.us", "");
	}

	/**
	 * Builds a full chat ID based on the context (group, private, or LID).
	 * 
	 * @return the full chat ID with the appropriate suffix.
	 * @throws IllegalArgumentException if chat ID is invalid.
	 */
	public String buildChatId() {
		if (getFrom().contains("@lid")) {
			return chatId + "@lid";
		}
		if (fromLid != null && !getFrom().contains("@g.us")) {
			return senderId + "@lid";
		}

		if (getCleanChatId() == null) {
			throw new IllegalArgumentException("Invalid WhatsApp DTO or clean chat ID");
		}

		String suffix = this.isGroupChat() ? "@g.us" : "@s.whatsapp.net";
		return this.getCleanChatId() + suffix;
	}
}
