package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendContactMessageDTO extends BaseMessageRequestDTO {
	private String contactName;
	private String contactPhone;


}
