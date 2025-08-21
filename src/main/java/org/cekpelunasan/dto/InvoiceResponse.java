package org.cekpelunasan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class InvoiceResponse {

	private String username;
	private Long amount;
	private String status;
	private String note;
	@JsonProperty("expires_at")
	private Long expiresAt;
	@JsonProperty("created_at")
	private String createdAt;
	@JsonProperty("qris_string")
	private String qrString;
	@JsonProperty("invoice_id")
	private String invoiceId;
}