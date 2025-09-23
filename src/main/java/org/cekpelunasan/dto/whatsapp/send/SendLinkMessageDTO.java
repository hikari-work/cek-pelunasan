package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendLinkMessageDTO extends BaseMessageRequestDTO {
	private String link;
	private String caption;

}
