package org.cekpelunasan.dto;

import lombok.Data;

import java.util.List;

/**
 * Data Transfer Object for IDEB (Informasi Debitur) data.
 * <p>
 * This class represents the structure of IDEB response containing header and individual debtor information.
 * </p>
 */
@Data
public class IdebDTO {
	/**
	 * Header information associated with the IDEB request/response.
	 */
	private HeaderDTO header;

	/**
	 * Individual debtor information.
	 */
	private IndividualDTO individual;

	/**
	 * DTO representing the header section of IDEB data.
	 */
	@Data
	public static class HeaderDTO {
		/**
		 * The request ID.
		 */
		private String idPermintaan;
		/**
		 * The user reference code.
		 */
		private String kodeReferensiPengguna;
	}

	/**
	 * DTO representing the individual section of IDEB data.
	 */
	@Data
	public static class IndividualDTO {
		/**
		 * List of primary debtor data.
		 */
		private List<DebiturDTO> dataPokokDebitur;
	}

	/**
	 * DTO representing the debtor details.
	 */
	@Data
	public static class DebiturDTO {
		/**
		 * Name of the debtor.
		 */
		private String namaDebitur;
	}
}
