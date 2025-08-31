package org.cekpelunasan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class WhatsappMessageDTO {

	private String from;

	private Message message;

	private String chat_id;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Image image;

	private String pushname;
	private String timestamp;

	@Data
	public static class Message {
		private String id;

		@JsonInclude(JsonInclude.Include.NON_NULL)
		private String text;
	}

	@Data
	public static class Image{
		private String media_path;
		private String mime_type;

		@JsonInclude(JsonInclude.Include.NON_NULL)
		private String caption;
	}
}
