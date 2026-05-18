package org.cekpelunasan.core.entity.simulasiangsuran;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class SimulasiAngsuranResult {

    private final String rekomendasiSkenario;
    private final BigDecimal totalBayarMinimum;
    private final List<SkenarioDetail> skenarioList;
}
