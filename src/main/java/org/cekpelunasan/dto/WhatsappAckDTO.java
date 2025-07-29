package org.cekpelunasan.dto;

import lombok.Data;

@Data
public class WhatsappAckDTO {
	private String event;
	private Payload payload;
	private String timestamp;

	@Data
	public static class Payload {
		private String chat_id;
		private String from;
		private String[] ids;
		private String receipt_type;
		private String receipt_type_description;
		private String sender_id;
	}
}

