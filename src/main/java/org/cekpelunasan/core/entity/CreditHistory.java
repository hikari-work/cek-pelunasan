package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
@Document(collection = "credit_history")
public class CreditHistory {

	/**
	 * The unique identifier for the credit history record.
	 */
	@Id
	private String id;

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
