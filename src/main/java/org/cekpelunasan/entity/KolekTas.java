package org.cekpelunasan.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "kolek_tas")
public class KolekTas {

	@Id
	@GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
	private Long id;

	private String kelompok;
	private String kantor;
	private String rekening;
	private String nama;
	private String alamat;
	private String noHp;
	private String kolek;
	private String nominal;
	private String accountOfficer;
	private String cif;
}
