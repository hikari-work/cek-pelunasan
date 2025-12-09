package org.cekpelunasan.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

import java.math.BigDecimal;

/**
 * Entity representing a savings account.
 * <p>
 * This stores details about a customer's savings account, including balance and
 * transactions.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
public class Savings {
	/**
	 * The unique identifier for the savings record.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	/**
	 * The branch code.
	 */
	private String branch;
	/**
	 * The type of savings account.
	 */
	private String type;
	/**
	 * The CIF (Customer Information File) number.
	 */
	private String cif;
	/**
	 * The savings account ID (tabId).
	 */
	private String tabId;
	/**
	 * The name of the account holder.
	 */
	private String name;
	/**
	 * The address of the account holder.
	 */
	private String address;
	/**
	 * The current balance.
	 */
	private BigDecimal balance;
	/**
	 * The transaction amount.
	 */
	private BigDecimal transaction;
	/**
	 * The account officer assigned.
	 */
	private String accountOfficer;
	/**
	 * The phone number of the account holder.
	 */
	private String phone;
	/**
	 * The minimum balance required.
	 */
	private BigDecimal minimumBalance;
	/**
	 * The amount blocked in the account.
	 */
	private BigDecimal blockingBalance;

}
