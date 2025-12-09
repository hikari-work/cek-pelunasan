package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * DTO for message revocation events received via webhook.
 * <p>
 * This class represents an event where a message has been revoked (deleted for
 * everyone).
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MessageRevokedDTO extends WebhookBaseDTO {
	/**
	 * The message that was revoked.
	 */
	private MessageDTO message;

	/**
	 * The ID of the chat where the message was revoked.
	 */
	@JsonProperty("revoked_chat")
	private String revokedChat;

	/**
	 * Indicates if the revocation was initiated by the current user.
	 */
	@JsonProperty("revoked_from_me")
	private Boolean revokedFromMe;

	/**
	 * The unique ID of the revoked message.
	 */
	@JsonProperty("revoked_message_id")
	private String revokedMessageId;
}
