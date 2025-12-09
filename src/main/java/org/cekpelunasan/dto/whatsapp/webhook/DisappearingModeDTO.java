package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Disappearing Mode DTO
/**
 * DTO for disappearing mode settings.
 * <p>
 * This class represents the configuration for disappearing messages in a chat.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisappearingModeDTO {
	/**
	 * The initiator of the disappearing mode.
	 */
	private Integer initiator;
	/**
	 * The trigger for disappearance.
	 */
	private Integer trigger;

	/**
	 * Indicates if the mode was initiated by the current user.
	 */
	@JsonProperty("initiatedByMe")
	private Boolean initiatedByMe;
}
