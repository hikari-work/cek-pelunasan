package org.cekpelunasan.dto;

import lombok.Data;

import java.util.List;

@Data
public class IdebDTO {
	private HeaderDTO header;
	private IndividualDTO individual;

	@Data
	public static class HeaderDTO {
		private String idPermintaan;
		private String kodeReferensiPengguna;
		// dll.
	}

	@Data
	public static class IndividualDTO {
		private List<DebiturDTO> dataPokokDebitur;
	}

	@Data
	public static class DebiturDTO {
		private String namaDebitur;
	}
}
