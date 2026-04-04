package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
@Document(collection = "kolek_tas")
public class KolekTas {

	/**
	 * The unique identifier for the record.
	 */
	@Id
	private String id;

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
