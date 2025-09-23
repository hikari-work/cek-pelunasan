package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Receipt Event Payload DTO
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptPayloadDTO {
	@JsonProperty("chat_id")
	private String chatId;

	private String from;
	private List<String> ids;

	@JsonProperty("receipt_type")
	private String receiptType;

	@JsonProperty("receipt_type_description")
	private String receiptTypeDescription;

	@JsonProperty("sender_id")
	private String senderId;
}
