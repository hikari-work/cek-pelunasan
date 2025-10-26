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

	public boolean isGroupChat() {
		return phone != null && phone.endsWith("@g.us");
	}

	public boolean isIndividualChat() {
		return phone != null && phone.endsWith("@s.whatsapp.net");
	}


}


