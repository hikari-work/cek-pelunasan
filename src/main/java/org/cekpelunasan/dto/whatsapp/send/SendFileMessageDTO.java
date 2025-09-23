package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendFileMessageDTO extends BaseMessageRequestDTO {
	private String caption;

}
