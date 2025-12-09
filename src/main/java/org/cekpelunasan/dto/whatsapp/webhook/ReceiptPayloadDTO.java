package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload DTO for receipt events.
 * <p>
 * Contains specific details about the receipt, such as type, sender, and
 * message IDs.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptPayloadDTO {
	/**
	 * The ID of the chat.
	 */
	@JsonProperty("chat_id")
	private String chatId;

	/**
	 * The sender's phone number.
	 */
	private String from;
	/**
	 * List of message IDs involved in the receipt.
	 */
	private List<String> ids;

	/**
	 * The type of receipt.
	 */
	@JsonProperty("receipt_type")
	private String receiptType;

	/**
	 * Description of the receipt type.
	 */
	@JsonProperty("receipt_type_description")
	private String receiptTypeDescription;

	/**
	 * The ID of the sender.
	 */
	@JsonProperty("sender_id")
	private String senderId;
}
