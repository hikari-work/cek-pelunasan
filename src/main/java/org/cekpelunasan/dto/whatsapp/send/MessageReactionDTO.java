package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * DTO for reacting to a message.
 * <p>
 * This class represents a request to add a reaction (emoji) to a specific
 * message.
 * </p>
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
public class MessageReactionDTO extends MessageActionDTO {
	/**
	 * The emoji to be used for the reaction.
	 */
	private String emoji;

}
