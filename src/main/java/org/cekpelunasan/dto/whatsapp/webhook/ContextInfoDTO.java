package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Context Info DTO
/**
 * DTO for context information in webhook messages.
 * <p>
 * This class contains additional context about a message, such as expiration
 * and disappearing mode settings.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContextInfoDTO {
	/**
	 * The expiration timestamp of the message.
	 */
	private Long expiration;

	/**
	 * The timestamp when ephemeral settings were configured.
	 */
	@JsonProperty("ephemeralSettingTimestamp")
	private Long ephemeralSettingTimestamp;

	/**
	 * The disappearing mode settings.
	 */
	@JsonProperty("disappearingMode")
	private DisappearingModeDTO disappearingMode;
}
