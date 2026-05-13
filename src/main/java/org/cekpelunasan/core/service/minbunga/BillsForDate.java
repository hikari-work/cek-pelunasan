package org.cekpelunasan.core.service.minbunga;

import org.cekpelunasan.core.entity.Bills;

import java.time.LocalDate;
import java.util.List;

public record BillsForDate(LocalDate targetDate, int daysDiff, List<DatedBill> bills) {}
