package org.cekpelunasan.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

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
@Entity
@Table(name = "payment")
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
