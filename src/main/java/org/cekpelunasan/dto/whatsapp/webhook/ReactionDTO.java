package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for reaction events received via webhook.
 * <p>
 * This class represents a reaction (emoji) applied to a message.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReactionDTO {
	/**
	 * The emoji used for the reaction.
	 */
	private String message;
	/**
	 * The ID of the message being reacted to.
	 */
	private String id;
}
