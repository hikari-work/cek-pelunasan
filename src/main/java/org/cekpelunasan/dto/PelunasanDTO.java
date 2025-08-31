package org.cekpelunasan.dto;

import lombok.Data;

@Data
public class PelunasanDTO {

	private String spk;
	private String name;
	private String address;
	private String product;
	private Long plafond;
	private Long amount;
	private Long interest;
	private double multiplier;
	private String penaltyType;
	private Long penalty;
	private Long denda;
	private Long total;
}
