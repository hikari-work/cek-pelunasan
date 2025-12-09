package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Message DTO
/**
 * DTO for text message content received via webhook.
 * <p>
 * This class represents a standard text message, including reply and quote
 * information.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
	/**
	 * The text content of the message.
	 */
	private String text;
	/**
	 * The unique identifier of the message.
	 */
	private String id;

	/**
	 * The ID of the message being replied to (if applicable).
	 */
	@JsonProperty("replied_id")
	private String repliedId;

	/**
	 * The content of the quoted message (if applicable).
	 */
	@JsonProperty("quoted_message")
	private String quotedMessage;
}
