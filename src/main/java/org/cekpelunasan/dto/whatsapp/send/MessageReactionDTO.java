package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

// DTO untuk react ke pesan
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
public class MessageReactionDTO extends MessageActionDTO {
	private String emoji;


}
