package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * DTO for message edition events received via webhook.
 * <p>
 * This class represents an event where a message has been edited.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MessageEditedDTO extends WebhookBaseDTO {
	/**
	 * The message content after edition.
	 */
	private MessageDTO message;

	/**
	 * The text content of the edited message.
	 */
	@JsonProperty("edited_text")
	private String editedText;
}
