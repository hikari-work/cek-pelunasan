package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * DTO for sending a text message.
 * <p>
 * This class represents a request to send a plain text message, optionally
 * replying to another message.
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendTextMessageDTO extends BaseMessageRequestDTO {
	/**
	 * The text content of the message.
	 */
	private String message;
	/**
	 * The ID of the message to reply to (optional).
	 */
	private String replyMessageId;
}
