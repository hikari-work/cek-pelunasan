package org.cekpelunasan.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class Paying {

	@Id
	private String id;

	private Boolean paid;
}
