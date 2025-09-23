package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MessageActionWebhookDTO extends BaseWebhookPayloadDTO {
	private String action;
	private String revokedMessageId;
	private String revokedChat;
	private Boolean revokedFromMe;
	private String editedText;


}
