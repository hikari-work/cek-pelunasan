package org.cekpelunasan.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Media DTO untuk berbagai jenis media
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaDTO {
	@JsonProperty("media_path")
	private String mediaPath;

	@JsonProperty("mime_type")
	private String mimeType;

	private String caption;
}
