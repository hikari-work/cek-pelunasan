package org.cekpelunasan.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
public class SimulasiResult {
	private long masukP;
	private long masukI;
	private long maxDate;

	public SimulasiResult(long masukP, long masukI, long maxDate) {
		this.masukP = masukP;
		this.masukI = masukI;
		this.maxDate = maxDate;
	}

	// Getters and Setters
}

