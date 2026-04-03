package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
@Document(collection = "customer_history")
public class CustomerHistory {

	/**
	 * The unique identifier for this history record.
	 */
	@Id
	private String id;

	/**
	 * The customer ID.
	 */
	private String customerId;

	/**
	 * The collection status recorded.
	 */
	private String collectStatus;
}
