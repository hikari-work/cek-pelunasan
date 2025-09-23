package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendMessageResponseDTO extends GenericResponseDTO {
	private SendMessageResult results;

	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	public static class SendMessageResult {
		private String messageId;
		private String status;


	}

}
