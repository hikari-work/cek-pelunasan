package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendPollMessageDTO extends BaseMessageRequestDTO {
	private String question;
	private String[] options;
	private Integer maxAnswer;


}
