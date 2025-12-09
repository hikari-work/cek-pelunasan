package org.cekpelunasan.dto.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for contact information received via webhook.
 * <p>
 * This class represents contact details shared in a message.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactDTO {
	/**
	 * The display name of the contact.
	 */
	private String displayName;
	/**
	 * The vCard data of the contact.
	 */
	private String vcard;

	/**
	 * Context information associated with the contact message.
	 */
	@JsonProperty("contextInfo")
	private ContextInfoDTO contextInfo;
}
