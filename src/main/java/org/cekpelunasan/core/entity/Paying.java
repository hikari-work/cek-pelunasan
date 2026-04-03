package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Entity representing a paying status.
 * <p>
 * This seems to track whether a specific entity (identified by ID) has paid.
 * </p>
 */
@Document(collection = "paying")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class Paying {

	/**
	 * The unique identifier.
	 */
	@Id
	private String id;

	/**
	 * Verified payment status.
	 */
	private Boolean paid;
}
