package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload DTO for group events.
 * <p>
 * Contains specific details about the group event, such as chat ID and
 * participant JIDs.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupEventPayloadDTO {
	/**
	 * The ID of the group chat.
	 */
	@JsonProperty("chat_id")
	private String chatId;

	/**
	 * The type of group event.
	 */
	private String type;
	/**
	 * List of JIDs involved in the event.
	 */
	private List<String> jids;
}
