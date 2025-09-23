package org.cekpelunasan.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

// Base DTO untuk semua webhook events
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookBaseDTO {
	@JsonProperty("sender_id")
	private String senderId;

	@JsonProperty("chat_id")
	private String chatId;

	private String from;

	private String timestamp;

	private String pushname;

	private String event;

	private String action;
}

