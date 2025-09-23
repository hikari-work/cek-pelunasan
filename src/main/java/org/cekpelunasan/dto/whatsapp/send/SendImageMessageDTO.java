package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendImageMessageDTO extends BaseMessageRequestDTO {
	private String caption;
	private Boolean viewOnce = false;
	private String imageUrl;
	private Boolean compress = false;

}
