package org.cekpelunasan.configuration;

import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.CreditHistory;
import org.cekpelunasan.core.entity.DataUpdateLog;
import org.cekpelunasan.core.entity.KolekTas;
import org.cekpelunasan.core.entity.MinBungaSession;
import org.cekpelunasan.core.entity.Paying;
import org.cekpelunasan.core.entity.Payment;
import org.cekpelunasan.core.entity.PaymentDetails;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.core.entity.Simulasi;
import org.cekpelunasan.core.entity.SimulasiResult;
import org.cekpelunasan.core.entity.SlikNotifiedFile;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.core.entity.simulasiangsuran.SimulasiAngsuranResult;
import org.cekpelunasan.core.entity.simulasiangsuran.SkenarioDetail;
import org.cekpelunasan.core.entity.simulasiangsuran.TahapPembayaran;
import org.cekpelunasan.miniapp.dto.CanvasSummaryDTO;
import org.cekpelunasan.miniapp.dto.KolekTasSummaryDTO;
import org.cekpelunasan.miniapp.dto.MiniAppAuthRequest;
import org.cekpelunasan.miniapp.dto.MiniAppAuthResponse;
import org.cekpelunasan.miniapp.dto.PaymentDetailDTO;
import org.cekpelunasan.miniapp.dto.PaymentRowDTO;
import org.cekpelunasan.miniapp.dto.PelunasanDetailDTO;
import org.cekpelunasan.miniapp.dto.TabunganSummaryDTO;
import org.cekpelunasan.miniapp.dto.TagihanSummaryDTO;
import org.cekpelunasan.miniapp.dto.UserInfoDTO;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class NativeRuntimeHints implements RuntimeHintsRegistrar {

	private static final Class<?>[] REFLECTIVE_TYPES = {
		AccountOfficerRoles.class,
		Bills.class,
		CreditHistory.class,
		DataUpdateLog.class,
		KolekTas.class,
		MinBungaSession.class,
		Paying.class,
		Payment.class,
		PaymentDetails.class,
		Savings.class,
		Simulasi.class,
		SimulasiResult.class,
		SlikNotifiedFile.class,
		User.class,
		SimulasiAngsuranResult.class,
		SkenarioDetail.class,
		TahapPembayaran.class,
		CanvasSummaryDTO.class,
		KolekTasSummaryDTO.class,
		MiniAppAuthRequest.class,
		MiniAppAuthResponse.class,
		PaymentDetailDTO.class,
		PaymentRowDTO.class,
		PelunasanDetailDTO.class,
		TabunganSummaryDTO.class,
		TagihanSummaryDTO.class,
		UserInfoDTO.class
	};

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		for (Class<?> type : REFLECTIVE_TYPES) {
			hints.reflection().registerType(type,
				MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
				MemberCategory.INVOKE_DECLARED_METHODS,
				MemberCategory.DECLARED_FIELDS);
		}

		hints.resources()
			.registerPattern("banner.txt")
			.registerPattern("application.properties")
			.registerPattern("static/*")
			.registerPattern("images/*")
			.registerPattern("org/apache/pdfbox/resources/*")
			.registerPattern("org/apache/fontbox/resources/*");
	}
}
