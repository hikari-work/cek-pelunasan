package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * DTO for sending contact cards.
 * <p>
 * This class represents a request to share a contact via WhatsApp.
 * </p>
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendContactMessageDTO extends BaseMessageRequestDTO {
	/**
	 * The name of the contact.
	 */
	private String contactName;
	/**
	 * The phone number of the contact.
	 */
	private String contactPhone;

}
