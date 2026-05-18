package org.cekpelunasan.platform.whatsapp.service.pelunasan;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.platform.whatsapp.service.dto.PelunasanDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Menghitung estimasi pelunasan kredit berdasarkan data tagihan yang ada.
 * <p>
 * Service ini adalah inti dari fitur cek pelunasan. Logikanya membedakan dua jenis kredit:
 * <ul>
 *   <li><strong>Flat Murni (LM)</strong> — dua kelas penalty berbeda untuk kredit jangka panjang
 *       (lebih dari 12 bulan) dan jangka pendek, dengan multiplier yang berkurang seiring waktu</li>
 *   <li><strong>Anuitas (DG)</strong> — penalty hanya 1x bunga tetap, kecuali kalau jatuh temponya
 *       bulan yang sama dengan bulan pelunasan (maka penalty 0)</li>
 * </ul>
 * Bunga yang dihitung juga mempertimbangkan apakah tanggal realisasi sudah lewat hari ini atau
 * belum, yang menentukan apakah bunga berjalan ikut ditambahkan atau tidak.
 * </p>
 */
@Slf4j
@Service
public class PelunasanService {

	// Constants
	private static final String FLAT_MURNI_TYPE = "LM";
	private static final String ANUITAS_TYPE = "DG";
	private static final int FLAT_MURNI_MAX_PENALTY_LONG_TERM = 6;
	private static final int FLAT_MURNI_MAX_PENALTY_SHORT_TERM = 3;
	private static final int FLAT_MURNI_MIN_PENALTY = 1;
	private static final int LONG_TERM_MONTHS = 12;
	private static final int SHORT_TERM_THRESHOLD = 6;
	private static final String TUNGGAKAN_BUNGA = "Tunggakan Bunga";
	private static final String TITIPAN_BUNGA = "Titipan Bunga";

	private static final String BUNGA = "Bunga";

	/**
	 * Menghitung semua komponen pelunasan dari data tagihan yang diberikan.
	 * <p>
	 * Proses perhitungannya:
	 * <ol>
	 *   <li>Validasi input — pastikan data tidak null dan formatnya benar</li>
	 *   <li>Identifikasi jenis kredit dari 2 karakter terakhir kode produk</li>
	 *   <li>Hitung multiplier penalty sesuai jenis kredit dan umur pinjaman</li>
	 *   <li>Hitung denda (tunggakan bunga + tunggakan pokok) dan penalty</li>
	 *   <li>Hitung bunga yang perlu dibayar dan tentukan tipenya (titipan/bunga/tunggakan)</li>
	 *   <li>Rakit semua komponen menjadi satu DTO yang siap ditampilkan</li>
	 * </ol>
	 * </p>
	 *
	 * @param pelunasan data tagihan nasabah yang akan dihitung pelunasannya
	 * @return DTO berisi semua komponen pelunasan yang sudah dihitung
	 * @throws IllegalArgumentException kalau data input tidak valid
	 */
	public PelunasanDto calculatePelunasn(Bills pelunasan) {
		validateInput(pelunasan);

		String creditType = extractCreditType(pelunasan.getProduct());
		int penaltyMultiplier = calculatePenaltyMultiplier(creditType, pelunasan);

		PenaltyCalculation penalty = calculatePenalties(pelunasan, penaltyMultiplier);

		InterestCalculation interest = calculateInterest(pelunasan);

		return buildPelunasanDto(pelunasan, penalty, interest, penaltyMultiplier);
	}

	private void validateInput(Bills pelunasan) {
		if (pelunasan == null) {
			throw new IllegalArgumentException("Bills cannot be null");
		}
		if (pelunasan.getProduct() == null || pelunasan.getProduct().length() < 2) {
			throw new IllegalArgumentException("Invalid product type");
		}
		if (pelunasan.getRealization() == null || pelunasan.getDueDate() == null) {
			throw new IllegalArgumentException("Realization and due date cannot be null");
		}
	}


	private String extractCreditType(String productType) {
		if (productType == null || productType.length() < 2) {
			throw new IllegalArgumentException("Product type must have at least 2 characters");
		}
		return productType.substring(productType.length() - 2);
	}

	private int calculatePenaltyMultiplier(String creditType, Bills pelunasan) {
		return switch (creditType) {
			case FLAT_MURNI_TYPE -> calculateFlatMurniPenaltyMultiplier(pelunasan);
			case ANUITAS_TYPE -> calculateAnuitasPenaltyMultiplier(pelunasan);
			default -> {
				log.warn("Unknown credit type: {}, defaulting to 0 multiplier", creditType);
				yield 0;
			}
		};
	}

	private PenaltyCalculation calculatePenalties(Bills pelunasan, int penaltyMultiplier) {
		Long denda = safeAdd(pelunasan.getPenaltyInterest(), pelunasan.getPenaltyPrincipal());
		Long penalty = safeMultiply(pelunasan.getFixedInterest(), penaltyMultiplier);

		return new PenaltyCalculation(denda, penalty);
	}

	private InterestCalculation calculateInterest(Bills pelunasan) {
		LocalDate realizationDate = parseDate(pelunasan.getRealization());
		boolean isAfterCurrentDay = isAfterCurrentDay(realizationDate);

		long baseInterest = pelunasan.getLastInterest() - pelunasan.getTitipan();
		long interestAmount = isAfterCurrentDay
			? baseInterest + pelunasan.getInterest()
			: baseInterest;

		String interestType;
		if (interestAmount < 0) {
			interestType = TITIPAN_BUNGA;
		} else if (interestAmount > 0) {
			interestType = TUNGGAKAN_BUNGA;
		} else {
			interestType = BUNGA;
		}


		return new InterestCalculation(interestAmount, interestType);
	}

	public PelunasanDto buildPelunasanDto(Bills pelunasan, PenaltyCalculation penalty,
										   InterestCalculation interest, int penaltyMultiplier) {
		PelunasanDto dto = new PelunasanDto();
		dto.setSpk(pelunasan.getNoSpk());
		dto.setNama(pelunasan.getName());
		dto.setRencanaPelunasan(getCurrentDate().toString());
		dto.setBakiDebet(pelunasan.getDebitTray());
		dto.setPlafond(pelunasan.getPlafond());
		dto.setAlamat(pelunasan.getAddress());
		dto.setDenda(penalty.denda());
		dto.setPerhitunganBunga(interest.amount());
		dto.setPenalty(penalty.penalty());
		dto.setTglRealisasi(pelunasan.getRealization());
		dto.setTglJatuhTempo(pelunasan.getDueDate());
		dto.setMultiplierPenalty(penaltyMultiplier);
		dto.setTypeBunga(interest.type());

		return dto;
	}

	private int calculateFlatMurniPenaltyMultiplier(Bills pelunasan) {
		LocalDate realizationDate = parseDate(pelunasan.getRealization());
		LocalDate dueDate = parseDate(pelunasan.getDueDate());
		LocalDate currentDate = getCurrentDate();

		int monthsPassed = calculateMonthsBetween(realizationDate, currentDate);
		int totalMonthsPeriod = calculateMonthsBetween(realizationDate, dueDate);
		int monthsLeft = totalMonthsPeriod - monthsPassed;

		if (isSameMonthAndYear(dueDate, LocalDate.now(ZoneOffset.ofHours(7)))) {
			return 0;
		}

		if (totalMonthsPeriod > LONG_TERM_MONTHS) {
			return calculateLongTermFlatMurniPenalty(monthsPassed, monthsLeft);
		} else {
			return calculateShortTermFlatMurniPenalty(monthsPassed, monthsLeft);
		}
	}

	private int calculateLongTermFlatMurniPenalty(int monthsPassed, int monthsLeft) {
		if (monthsPassed < LONG_TERM_MONTHS) {
			return Math.min(FLAT_MURNI_MAX_PENALTY_LONG_TERM, monthsLeft);
		}
		return Math.min(FLAT_MURNI_MAX_PENALTY_SHORT_TERM, monthsLeft);
	}

	private int calculateShortTermFlatMurniPenalty(int monthsPassed, int monthsLeft) {
		if (monthsPassed > SHORT_TERM_THRESHOLD) {
			return Math.min(FLAT_MURNI_MIN_PENALTY, monthsLeft);
		} else {
			return Math.min(FLAT_MURNI_MAX_PENALTY_SHORT_TERM, monthsLeft);
		}
	}

	private int calculateAnuitasPenaltyMultiplier(Bills pelunasan) {
		LocalDate dueDate = parseDate(pelunasan.getDueDate());

		boolean isSameMonthAndYear = isSameMonthAndYear(LocalDate.now(ZoneOffset.ofHours(7)), dueDate);
		return isSameMonthAndYear ? 0 : 1;
	}


	private LocalDate parseDate(String dateString) {
		if (dateString == null || dateString.trim().isEmpty()) {
			throw new IllegalArgumentException("Date string cannot be null or empty");
		}

		try {
			String[] parts = dateString.split("-");
			if (parts.length != 3) {
				throw new IllegalArgumentException("Date must be in format YYYY-MM-DD");
			}

			int year = Integer.parseInt(parts[0]);
			int month = Integer.parseInt(parts[1]);
			int day = Integer.parseInt(parts[2]);

			return LocalDate.of(year, month, day);
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			throw new IllegalArgumentException("Invalid date format: " + dateString, e);
		}
	}

	private LocalDate getCurrentDate() {
		return LocalDate.now(ZoneOffset.ofHours(7));
	}

	private boolean isAfterCurrentDay(LocalDate date) {
		return date.getDayOfMonth() > getCurrentDate().getDayOfMonth();
	}

	private boolean isSameMonthAndYear(LocalDate date1, LocalDate date2) {
		log.info("Checking if {} and {} are same month and year ", date1, date2);
		return date1.getMonthValue() == date2.getMonthValue()
			&& date1.getYear() == date2.getYear();
	}

	private int calculateMonthsBetween(LocalDate startDate, LocalDate endDate) {
		int yearsDiff = endDate.getYear() - startDate.getYear();
		int monthsDiff = endDate.getMonthValue() - startDate.getMonthValue();
		return yearsDiff * 12 + monthsDiff;
	}

	private Long safeAdd(Long a, Long b) {
		if (a == null) a = 0L;
		if (b == null) b = 0L;
		return a + b;
	}

	private Long safeMultiply(Long a, Integer b) {
		if (a == null) a = 0L;
		if (b == null) b = 0;
		return a * b;
	}


	public record PenaltyCalculation(Long denda, Long penalty) {}

	public record InterestCalculation(Long amount, String type) {}
}
