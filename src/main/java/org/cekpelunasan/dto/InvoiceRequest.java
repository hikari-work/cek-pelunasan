package org.cekpelunasan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvoiceRequest {

	@JsonProperty("notes")
	@NotBlank
	private String notes;
	@JsonProperty("amount")
	private Long amount;
	@JsonProperty("expires_at")
	private Long expiresAt;
}

