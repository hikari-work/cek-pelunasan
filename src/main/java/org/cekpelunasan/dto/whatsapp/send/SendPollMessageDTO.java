package org.cekpelunasan.dto.whatsapp.send;

import lombok.*;

/**
 * DTO for sending a poll message.
 * <p>
 * This class represents a request to create a poll with a question and set of
 * options.
 * </p>
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendPollMessageDTO extends BaseMessageRequestDTO {
	/**
	 * The question of the poll.
	 */
	private String question;
	/**
	 * Array of options for the poll.
	 */
	private String[] options;
	/**
	 * Maximum number of answers allowed.
	 */
	private Integer maxAnswer;

}
