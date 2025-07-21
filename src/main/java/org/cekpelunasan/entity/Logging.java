package org.cekpelunasan.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "logging")
public class Logging {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Lob
	private String request;

	@Lob
	private String response;
}
