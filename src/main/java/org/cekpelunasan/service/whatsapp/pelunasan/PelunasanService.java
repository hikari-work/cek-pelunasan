package org.cekpelunasan.service.whatsapp.pelunasan;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.service.whatsapp.dto.PelunasanDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

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

	public PelunasanDto calculatePelunasn(Bills pelunasan) {
		validateInput(pelunasan);

		String creditType = extractCreditType(pelunasan.getProduct());
		int penaltyMultiplier = calculatePenaltyMultiplier(creditType, pelunasan);

		log.info("Credit Type: {}, Penalty Multiplier: {}", creditType, penaltyMultiplier);

		PenaltyCalculation penalty = calculatePenalties(pelunasan, penaltyMultiplier);

		InterestCalculation interest = calculateInterest(pelunasan);

		log.info("Date comparison - Is after now: {}", isAfterCurrentDay(parseDate(pelunasan.getRealization())));
		log.info("Interest calculation - Amount: {}", interest.amount());

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

		log.info("FM Penalty calculation - Passed: {}, Left: {}, Total: {}",
			monthsPassed, monthsLeft, totalMonthsPeriod);
		if (isSameMonthAndYear(dueDate, LocalDate.now())) {
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
		LocalDate realizationDate = parseDate(pelunasan.getRealization());
		LocalDate dueDate = parseDate(pelunasan.getDueDate());

		log.info("Anuitas penalty calculation - Realization: {}, Due date: {}",
			realizationDate, dueDate);

		boolean isSameMonthAndYear = isSameMonthAndYear(realizationDate, dueDate);
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
		return LocalDate.now();
	}

	private boolean isAfterCurrentDay(LocalDate date) {
		return date.getDayOfMonth() > getCurrentDate().getDayOfMonth();
	}

	private boolean isSameMonthAndYear(LocalDate date1, LocalDate date2) {
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