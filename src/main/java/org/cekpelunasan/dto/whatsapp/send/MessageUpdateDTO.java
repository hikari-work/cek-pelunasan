package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * DTO for updating a message.
 * <p>
 * This class represents a request to edit the content of a sent message.
 * </p>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class MessageUpdateDTO extends MessageActionDTO {
	/**
	 * The new content of the message.
	 */
	private String message;

}
