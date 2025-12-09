package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * DTO for regular messages received via webhook.
 * <p>
 * This class encapsulates various message types including text, media,
 * reactions, locations, and contacts.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RegularMessageDTO extends WebhookBaseDTO {
	/**
	 * The text message content.
	 */
	private MessageDTO message;

	// Media fields
	/**
	 * Image media content.
	 */
	private MediaDTO image;
	/**
	 * Video media content.
	 */
	private MediaDTO video;
	/**
	 * Audio media content.
	 */
	private MediaDTO audio;
	/**
	 * Document media content.
	 */
	private MediaDTO document;
	/**
	 * Sticker media content.
	 */
	private MediaDTO sticker;

	// Other message types
	/**
	 * Reaction content.
	 */
	private ReactionDTO reaction;
	/**
	 * Location content.
	 */
	private LocationDTO location;
	/**
	 * Contact content.
	 */
	private ContactDTO contact;

	// Special flags
	/**
	 * Indicates if the message is view once.
	 */
	@JsonProperty("view_once")
	private Boolean viewOnce;

	/**
	 * Indicates if the message was forwarded.
	 */
	private Boolean forwarded;
}
