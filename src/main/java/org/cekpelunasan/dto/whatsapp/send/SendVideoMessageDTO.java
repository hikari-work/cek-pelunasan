package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

// DTO untuk mengirim video
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendVideoMessageDTO extends BaseMessageRequestDTO {
	private String caption;
	private Boolean viewOnce = false;
	private String videoUrl;
	private Boolean compress = false;


}
