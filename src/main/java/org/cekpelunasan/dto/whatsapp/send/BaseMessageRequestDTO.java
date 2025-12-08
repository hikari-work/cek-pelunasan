package org.cekpelunasan.dto.whatsapp.send;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class BaseMessageRequestDTO {
	private String phone;
	private Boolean isForwarded = false;
	private Integer duration;
	public BaseMessageRequestDTO() {}

	public BaseMessageRequestDTO(String phone) {
		this.phone = phone;
	}
}


