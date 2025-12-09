package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * Generic response DTO for API responses.
 * <p>
 * This class encapsulates a standard response format containing a code,
 * message, and optional results.
 * </p>
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GenericResponseDTO {
	/**
	 * Status code of the response.
	 */
	private String code;
	/**
	 * Descriptive message regarding the response status.
	 */
	private String message;
	/**
	 * The payload or result data of the response.
	 */
	private Object results;

}
