package org.cekpelunasan.dto.whatsapp.send;


import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SendFileMessageDTO extends BaseMessageRequestDTO {
	private String caption;
	private byte[] fileBytes;
	private String fileName;
	private Boolean isForwarded = false;
	private Integer duration = 3600;
	private String videoUrl;
	private String audioUrl;
	private String stickerUrl;
	private String contactName;
	private String contactPhone;
	private String locationUrl;
	private String latitude;
	private String longitude;
	private String link;
	private String imageUrl;
}
