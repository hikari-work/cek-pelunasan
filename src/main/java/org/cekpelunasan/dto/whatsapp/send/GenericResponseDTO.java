package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;


@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GenericResponseDTO {
	private String code;
	private String message;
	private Object results;


}
