package org.cekpelunasan.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

/**
 * Entity representing 'Kolek Tas' (Collection Task/Bag).
 * <p>
 * This entity likely represents a collection of tasks or accounts assigned for
 * collection.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "kolek_tas")
public class KolekTas {

	/**
	 * The unique identifier for the record.
	 */
	@Id
	@GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
	private Long id;

	/**
	 * The group (kelompok) the record belongs to.
	 */
	private String kelompok;
	/**
	 * The office (kantor) associated with the record.
	 */
	private String kantor;
	/**
	 * The account number (rekening).
	 */
	private String rekening;
	/**
	 * The name associated with the record.
	 */
	private String nama;
	/**
	 * The address associated with the record.
	 */
	private String alamat;
	/**
	 * The phone number.
	 */
	private String noHp;
	/**
	 * The collection status/level (kolek).
	 */
	private String kolek;
	/**
	 * The nominal amount involved.
	 */
	private String nominal;
	/**
	 * The account officer assigned.
	 */
	private String accountOfficer;
	/**
	 * The CIF (Customer Information File) number.
	 */
	private String cif;
}
