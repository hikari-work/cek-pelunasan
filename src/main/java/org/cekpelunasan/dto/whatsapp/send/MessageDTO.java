package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
	private String text;
	private String id;
	private String repliedId;
	private String quotedMessage;


}
