package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ReceiptWebhookDTO {
	private String event;
	private ReceiptPayloadDTO payload;
	private String timestamp;

	@Setter
	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ReceiptPayloadDTO {
		private String chatId;
		private String from;
		private String[] ids;
		private String receiptType;
		private String receiptTypeDescription;
		private String senderId;

	}

}
