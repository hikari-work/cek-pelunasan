package org.cekpelunasan.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing a simulation calculation (Simulasi).
 * <p>
 * This entity stores parameters and results related to financial simulations,
 * likely for credit or repayment scenarios.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "simulasi")
public class Simulasi {

	/**
	 * The unique identifier for the simulation (UUID).
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	/**
	 * The SPK number associated with the simulation.
	 */
	private String spk;
	/**
	 * The date of the simulation.
	 */
	private String tanggal;
	/**
	 * The sequence number or order.
	 */
	private String sequence;
	/**
	 * The arrears amount (tunggakan).
	 */
	private Long tunggakan;
	/**
	 * The penalty amount (denda).
	 */
	private Long denda;
	/**
	 * The late payment charge or days (keterlambatan).
	 */
	private Long keterlambatan;
}
