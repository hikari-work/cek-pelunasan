package org.cekpelunasan.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SendMessageResponse {
	private String code;
	private String message;
	private Results results;

	@Data
	public static class Results {
		@JsonProperty("message_id")
		private String messageId;
		private String status;
	}
}