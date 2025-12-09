package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

// Group Event DTO
/**
 * DTO for group events received via webhook.
 * <p>
 * This class represents events related to group chats, such as member additions
 * or removals.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class GroupEventDTO extends WebhookBaseDTO {
	/**
	 * The payload of the group event.
	 */
	private GroupEventPayloadDTO payload;
}
