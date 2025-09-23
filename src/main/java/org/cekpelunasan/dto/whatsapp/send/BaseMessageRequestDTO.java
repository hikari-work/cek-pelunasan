package org.cekpelunasan.dto.whatsapp.send;


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


	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }

	public Boolean getIsForwarded() { return isForwarded; }
	public void setIsForwarded(Boolean isForwarded) { this.isForwarded = isForwarded; }

	public Integer getDuration() { return duration; }
	public void setDuration(Integer duration) { this.duration = duration; }
}


