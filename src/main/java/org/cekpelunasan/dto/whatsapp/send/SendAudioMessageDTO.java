package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

// DTO untuk mengirim audio
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendAudioMessageDTO extends BaseMessageRequestDTO {
	private String audioUrl;

}
