package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Location DTO
/**
 * DTO for location information received via webhook.
 * <p>
 * This class represents geographic location data shared in a message.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationDTO {
	/**
	 * The latitude in degrees.
	 */
	@JsonProperty("degreesLatitude")
	private Double degreesLatitude;

	/**
	 * The longitude in degrees.
	 */
	@JsonProperty("degreesLongitude")
	private Double degreesLongitude;

	/**
	 * The name of the location (if available).
	 */
	private String name;
	/**
	 * The address of the location (if available).
	 */
	private String address;

	/**
	 * Base64 encoded JPEG thumbnail of the location map.
	 */
	@JsonProperty("JPEGThumbnail")
	private String jpegThumbnail;

	/**
	 * Context information associated with the location message.
	 */
	@JsonProperty("contextInfo")
	private ContextInfoDTO contextInfo;
}
