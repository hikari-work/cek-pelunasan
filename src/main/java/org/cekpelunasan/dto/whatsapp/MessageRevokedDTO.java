package org.cekpelunasan.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;


@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MessageRevokedDTO extends WebhookBaseDTO {
	private MessageDTO message;

	@JsonProperty("revoked_chat")
	private String revokedChat;

	@JsonProperty("revoked_from_me")
	private Boolean revokedFromMe;

	@JsonProperty("revoked_message_id")
	private String revokedMessageId;
}
