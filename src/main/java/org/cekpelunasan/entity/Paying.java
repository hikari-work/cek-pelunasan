package org.cekpelunasan.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

/**
 * Entity representing a paying status.
 * <p>
 * This seems to track whether a specific entity (identified by ID) has paid.
 * </p>
 */
@Entity
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
