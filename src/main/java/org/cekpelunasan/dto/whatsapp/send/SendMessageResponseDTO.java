package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * Response DTO for message sending requests.
 * <p>
 * Contains specific results related to the message sent, such as message ID and
 * status.
 * </p>
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendMessageResponseDTO extends GenericResponseDTO {
	/**
	 * Result details of the send operation.
	 */
	private SendMessageResult results;

	/**
	 * Detailed result of the message sending.
	 */
	@NoArgsConstructor
	@AllArgsConstructor
	@Getter
	@Setter
	public static class SendMessageResult {
		/**
		 * The unique identifier of the sent message.
		 */
		private String messageId;
		/**
		 * The status of the message status (e.g., pending, sent).
		 */
		private String status;

	}

}
