package org.cekpelunasan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WhatsappWrapperDTO {
	private String event;
	private String timestamp;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private WhatsappMessageDTO payload;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private WhatsappAckDTO ackPayload;
}
