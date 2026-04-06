package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Entity representing a payment transaction.
 * <p>
 * This tracks payment amounts and their status for a specific user.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "payment")
public class Payment {

	/**
	 * The unique identifier for the payment.
	 */
	@Id
	private String id;

	/**
	 * The amount of the payment.
	 */
	private Long amount;

	/**
	 * The user associated with the payment.
	 */
	private String user;

	/**
	 * Indicates if the payment has been completed.
	 */
	private boolean isPaid;
}
