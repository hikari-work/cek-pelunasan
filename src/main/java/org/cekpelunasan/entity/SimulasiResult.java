package org.cekpelunasan.entity;

import lombok.*;

/**
 * POJO representing the result of a simulation.
 * <p>
 * This class holds the calculated values from a simulation process.
 * Not an entity, but a result holder.
 * </p>
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SimulasiResult {
	/**
	 * Calculated input P (Principal?).
	 */
	private long masukP;
	/**
	 * Calculated input I (Interest?).
	 */
	private long masukI;
	/**
	 * Maximum date or deadline involved in the simulation.
	 */
	private long maxDate;

}
