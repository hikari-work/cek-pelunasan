package org.cekpelunasan.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

/**
 * Entity representing the history of a customer's collection status.
 * <p>
 * This entity tracks changes in the customer's collection status over time.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity(name = "customer_history")
public class CustomerHistory {

	/**
	 * The unique identifier for this history record.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * The customer ID.
	 */
	private String customerId;

	/**
	 * The collection status recorded.
	 */
	private String collectStatus;
}
