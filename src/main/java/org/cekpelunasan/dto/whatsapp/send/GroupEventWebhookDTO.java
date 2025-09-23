package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
public class GroupEventWebhookDTO {
	private String event;
	private GroupEventPayloadDTO payload;
	private String timestamp;

	@Setter
	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class GroupEventPayloadDTO {
		private String chatId;
		private String type;
		private String[] jids;

	}

}
