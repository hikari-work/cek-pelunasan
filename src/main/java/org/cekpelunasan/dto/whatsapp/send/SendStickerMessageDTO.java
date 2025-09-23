package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendStickerMessageDTO extends BaseMessageRequestDTO {
	private String stickerUrl;

}
