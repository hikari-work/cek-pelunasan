package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * DTO for receipt events received via webhook.
 * <p>
 * This class represents message receipt updates (e.g., delivered, read).
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ReceiptEventDTO extends WebhookBaseDTO {
	/**
	 * The payload containing receipt details.
	 */
	private ReceiptPayloadDTO payload;
}
