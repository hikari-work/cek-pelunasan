package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendTextMessageDTO extends BaseMessageRequestDTO {
	private String message;
	private String replyMessageId;
}
