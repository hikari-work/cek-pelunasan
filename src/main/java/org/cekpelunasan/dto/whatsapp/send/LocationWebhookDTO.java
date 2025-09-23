package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LocationWebhookDTO extends BaseWebhookPayloadDTO {
	private LocationDTO location;

	@Setter
	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class LocationDTO {
		private Double degreesLatitude;
		private Double degreesLongitude;
		private String name;
		private String address;
		private String jpegThumbnail;


	}

}
