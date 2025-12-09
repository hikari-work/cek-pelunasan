package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * DTO for incoming receipt webhooks.
 * <p>
 * This class represents the structure of receipt events (e.g., delivered, read)
 * received via webhook.
 * </p>
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ReceiptWebhookDTO {
	/**
	 * The name of the event.
	 */
	private String event;
	/**
	 * The payload of the receipt.
	 */
	private ReceiptPayloadDTO payload;
	/**
	 * The timestamp of the event.
	 */
	private String timestamp;

	/**
	 * Payload details for the receipt event.
	 */
	@Setter
	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ReceiptPayloadDTO {
		/**
		 * The ID of the chat where the message was sent.
		 */
		private String chatId;
		/**
		 * The sender's phone number.
		 */
		private String from;
		/**
		 * Array of message IDs associated with the receipt.
		 */
		private String[] ids;
		/**
		 * The type of receipt (e.g., delivered, read).
		 */
		private String receiptType;
		/**
		 * Description of the receipt type.
		 */
		private String receiptTypeDescription;
		/**
		 * The ID of the sender.
		 */
		private String senderId;

	}

}
