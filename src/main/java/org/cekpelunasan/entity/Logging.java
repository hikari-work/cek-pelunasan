package org.cekpelunasan.entity;

import jakarta.persistence.*;
import lombok.*;

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
@Entity
@Table(name = "logging")
public class Logging {

	/**
	 * Unique identifier for the log entry (UUID).
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	/**
	 * The raw request content. stored as Large Object (Lob).
	 */
	@Lob
	private String request;

	/**
	 * The raw response content, stored as Large Object (Lob).
	 */
	@Lob
	private String response;
}
