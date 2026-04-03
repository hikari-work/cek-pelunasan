package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Entity for logging HTTP requests and responses.
 * <p>
 * This entity stores the raw request and response data for auditing or
 * debugging purposes.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "logging")
public class Logging {

	/**
	 * Unique identifier for the log entry (UUID).
	 */
	@Id
	private String id;

	/**
	 * The raw request content.
	 */
	private String request;

	/**
	 * The raw response content.
	 */
	private String response;
}
