package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

// Regular Message DTO
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RegularMessageDTO extends WebhookBaseDTO {
	private MessageDTO message;

	// Media fields
	private MediaDTO image;
	private MediaDTO video;
	private MediaDTO audio;
	private MediaDTO document;
	private MediaDTO sticker;

	// Other message types
	private ReactionDTO reaction;
	private LocationDTO location;
	private ContactDTO contact;

	// Special flags
	@JsonProperty("view_once")
	private Boolean viewOnce;

	private Boolean forwarded;
}
