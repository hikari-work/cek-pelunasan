package org.cekpelunasan.dto.whatsapp.send;

import lombok.AllArgsConstructor;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * DTO for sending a location message.
 * <p>
 * This class represents a request to send geographic coordinates.
 * </p>
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendLocationMessageDTO extends BaseMessageRequestDTO {
	/**
	 * Latitude of the location.
	 */
	private String latitude;
	/**
	 * Longitude of the location.
	 */
	private String longitude;
}
