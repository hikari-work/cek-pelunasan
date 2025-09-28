package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;


@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReactionWebhookDTO extends BaseWebhookPayloadDTO {
	private ReactionDTO reaction;

	@Setter
	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ReactionDTO {
		private String message;
		private String id;


	}

}
