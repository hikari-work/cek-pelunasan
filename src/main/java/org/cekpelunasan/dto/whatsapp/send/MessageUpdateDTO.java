package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class MessageUpdateDTO extends MessageActionDTO {
	private String message;


}
