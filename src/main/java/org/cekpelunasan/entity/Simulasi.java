package org.cekpelunasan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.stereotype.Component;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "simulasi")
public class Simulasi {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	private String spk;
	private String tanggal;
	private String sequence;
	private Long tunggakan;
	private Long denda;
	private Long keterlambatan;
}
