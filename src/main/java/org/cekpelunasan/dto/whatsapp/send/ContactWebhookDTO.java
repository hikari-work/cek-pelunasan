package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@NoArgsConstructor
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContactWebhookDTO extends BaseWebhookPayloadDTO {
	private ContactDTO contact;

	@Setter
	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ContactDTO {
		private String displayName;
		private String vcard;

	}

}
