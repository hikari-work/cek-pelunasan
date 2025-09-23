package org.cekpelunasan.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

// Message Edited DTO
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MessageEditedDTO extends WebhookBaseDTO {
	private MessageDTO message;

	@JsonProperty("edited_text")
	private String editedText;
}
