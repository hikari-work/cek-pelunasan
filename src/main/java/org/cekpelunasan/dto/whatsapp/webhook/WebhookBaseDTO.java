package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base DTO for all webhook events.
 * <p>
 * Provides common fields shared across different webhook event types, such as
 * sender ID, chat ID, and timestamp.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookBaseDTO {
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
	 * The sender's phone number or identifier.
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
}
