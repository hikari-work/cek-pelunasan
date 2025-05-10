package org.cekpelunasan.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
public class Savings {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String branch;
	private String type;
	private String cif;
	private String tabId;
	private String name;
	private String address;
	private BigDecimal balance;
	private BigDecimal transaction;
	private String accountOfficer;
	private String phone;
	private BigDecimal minimumBalance;
	private BigDecimal blockingBalance;

}
