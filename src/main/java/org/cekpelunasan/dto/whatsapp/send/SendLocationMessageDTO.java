package org.cekpelunasan.dto.whatsapp.send;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendLocationMessageDTO extends BaseMessageRequestDTO {
	private String latitude;
	private String longitude;
}
