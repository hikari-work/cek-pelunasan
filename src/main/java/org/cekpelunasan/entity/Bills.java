package org.cekpelunasan.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "tagihan", indexes = {@Index(name = "idx_name", columnList = "name"),
				@Index(name = "idx_branch", columnList = "branch")})
public class Bills {

	private String customerId;
	private String wilayah;
	private String branch;
	@Id
	private String noSpk;
	private String officeLocation;
	private String product;
	private String name;
	private String address;
	private String payDown;
	private String realization;
	private String dueDate;
	private String collectStatus;
	private String dayLate;
	private Long plafond;
	private Long debitTray;
	private Long interest;
	private Long principal;
	private Long installment;
	private Long lastInterest;
	private Long lastPrincipal;
	private Long lastInstallment;
	private Long fullPayment;
	private Long minInterest;
	private Long minPrincipal;
	private Long penaltyInterest;
	private Long penaltyPrincipal;
	private String accountOfficer;
}
