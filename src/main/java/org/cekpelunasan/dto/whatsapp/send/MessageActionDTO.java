package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * Base DTO for message actions.
 * <p>
 * This class is used for actions related to existing messages, such as deleting
 * or updating.
 * </p>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MessageActionDTO {
	/**
	 * The phone number associated with the action.
	 */
	private String phone;
	/**
	 * The unique ID of the message to act upon.
	 */
	private String messageId;

}
