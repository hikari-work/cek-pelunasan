package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MediaMessageWebhookDTO extends BaseWebhookPayloadDTO {
	private MediaInfoDTO image;
	private MediaInfoDTO video;
	private MediaInfoDTO audio;
	private MediaInfoDTO document;
	private MediaInfoDTO sticker;
	private Boolean viewOnce;
	private Boolean forwarded;

	@Setter
	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class MediaInfoDTO {
		private String mediaPath;
		private String mimeType;
		private String caption;


	}

}
