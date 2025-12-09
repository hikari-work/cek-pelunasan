package org.cekpelunasan.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

/**
 * Entity representing the credit history of a customer.
 * <p>
 * This stores historical data related to credit applications or checks.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity(name = "credit_history")
public class CreditHistory {

	/**
	 * The unique identifier for the credit history record.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * The date of the credit history record (timestamp).
	 */
	private Long date;

	/**
	 * The credit ID associated with the record.
	 */
	private String creditId;

	/**
	 * The customer ID associated with the credit history.
	 */
	private String customerId;

	/**
	 * The name of the customer.
	 */
	private String name;

	/**
	 * The status of the credit history (e.g., approved, rejected).
	 */
	private String status;

	/**
	 * The address of the customer.
	 */
	private String address;

	/**
	 * The phone number of the customer.
	 */
	private String phone;

}
