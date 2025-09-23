package org.cekpelunasan.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Message DTO
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
	private String text;
	private String id;

	@JsonProperty("replied_id")
	private String repliedId;

	@JsonProperty("quoted_message")
	private String quotedMessage;
}
