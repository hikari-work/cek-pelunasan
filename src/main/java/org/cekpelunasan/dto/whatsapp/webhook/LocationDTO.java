package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Location DTO
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationDTO {
	@JsonProperty("degreesLatitude")
	private Double degreesLatitude;

	@JsonProperty("degreesLongitude")
	private Double degreesLongitude;

	private String name;
	private String address;

	@JsonProperty("JPEGThumbnail")
	private String jpegThumbnail;

	@JsonProperty("contextInfo")
	private ContextInfoDTO contextInfo;
}
