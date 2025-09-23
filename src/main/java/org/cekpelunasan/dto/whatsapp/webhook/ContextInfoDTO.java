package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Context Info DTO
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContextInfoDTO {
	private Long expiration;

	@JsonProperty("ephemeralSettingTimestamp")
	private Long ephemeralSettingTimestamp;

	@JsonProperty("disappearingMode")
	private DisappearingModeDTO disappearingMode;
}
