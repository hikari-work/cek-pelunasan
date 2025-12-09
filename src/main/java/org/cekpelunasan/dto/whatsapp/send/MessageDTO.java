package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * DTO representing a WhatsApp message.
 * <p>
 * Contains details about the message text, ID, and reply context.
 * </p>
 */
@NoArgsConstructor

public class MessageDTO {
	private String text;
	private String id;
	private String repliedId;
	private String quotedMessage;

}
