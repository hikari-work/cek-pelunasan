package org.cekpelunasan.service.repayment;

import org.cekpelunasan.dto.PelunasanDTO;
import org.cekpelunasan.entity.Bills;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

@Component
public class PelunasanService {

	private static final Logger logger = LoggerFactory.getLogger(PelunasanService.class);

	public PelunasanDTO getPelunasanById(Bills bills) {
		if (bills == null) {
			throw new IllegalArgumentException("Bills parameter cannot be null");
		}
		PelunasanDTO pelunasanDTO = new PelunasanDTO();
		pelunasanDTO.setSpk(bills.getNoSpk());

		pelunasanDTO.setName(bills.getName() != null ? bills.getName() : "");
		pelunasanDTO.setAddress(bills.getAddress() != null ? bills.getAddress() : "");
		pelunasanDTO.setPlafond(bills.getPlafond() != null ? bills.getPlafond() : 0L);
		pelunasanDTO.setAmount(bills.getDebitTray() != null ? bills.getDebitTray() : 0L);
		pelunasanDTO.setProduct(bills.getProduct() != null ? bills.getProduct() : "");

		pelunasanDTO.setMultiplier(switch (typeProduct(bills)) {
			case "DG" -> diagonalMultiplier(bills);
			case "LM" -> flatMultiplier(bills);
			case "BT" -> fixedMonth(bills);
			default -> 0;
		});

		long lastInterest = bills.getLastInterest() != null ? bills.getLastInterest() : 0L;
		long interest = bills.getInterest() != null ? bills.getInterest() : 0L;
		pelunasanDTO.setInterest(isAfterDatePay(bills) ? lastInterest : interest + lastInterest);

		long titipan = bills.getTitipan() != null ? bills.getTitipan() : 0L;
		pelunasanDTO.setPenaltyType(titipan > 0 ? "Titipan Bunga" : "Tunggakan Bunga");

		double fixedInterest = bills.getFixedInterest() != null ? bills.getFixedInterest() : 0.0;
		pelunasanDTO.setPenalty((long) (fixedInterest * pelunasanDTO.getMultiplier()));

		Long penaltyInterest = bills.getPenaltyInterest() != null ? bills.getPenaltyInterest() : 0L;
		Long penaltyPrincipal = bills.getPenaltyPrincipal() != null ? bills.getPenaltyPrincipal() : 0L;
		pelunasanDTO.setDenda(penaltyInterest + penaltyPrincipal);

		pelunasanDTO.setTotal(pelunasanDTO.getAmount() + pelunasanDTO.getInterest() + pelunasanDTO.getPenalty() + pelunasanDTO.getDenda());

		return pelunasanDTO;
	}

	public boolean isAfterDatePay(Bills bills) {
		try {
			if (bills.getRealization() == null) {
				return false;
			}

			LocalDate realization = LocalDate.parse(bills.getRealization(), DateTimeFormatter.ISO_DATE);
			LocalDate now = LocalDate.now();
			return realization.getDayOfMonth() == now.getDayOfMonth();

		} catch (DateTimeParseException e) {
			logger.error("Error parsing realization date: {}", e.getMessage());
			return false;
		}
	}

	public long timePeriod(Bills bills) {
		try {
			if (bills.getRealization() == null || bills.getDueDate() == null) {
				return 0L;
			}

			LocalDate firstDate = LocalDate.parse(bills.getRealization(), DateTimeFormatter.ISO_DATE);
			LocalDate secondDate = LocalDate.parse(bills.getDueDate(), DateTimeFormatter.ISO_DATE);
			return ChronoUnit.MONTHS.between(firstDate, secondDate);

		} catch (DateTimeParseException e) {
			logger.error("Error parsing dates for timePeriod: {}", e.getMessage());
			return 0L;
		}
	}

	public long payPeriod(Bills bills) {
		try {
			if (bills.getRealization() == null) {
				return 0L;
			}

			LocalDate firstDate = LocalDate.parse(bills.getRealization(), DateTimeFormatter.ISO_DATE);
			LocalDate now = LocalDate.now();
			return ChronoUnit.MONTHS.between(firstDate, now);

		} catch (DateTimeParseException e) {
			logger.error("Error parsing realization date for payPeriod: {}", e.getMessage());
			return 0L;
		}
	}

	public long leftPeriod(Bills bills) {
		try {
			if (bills.getDueDate() == null) {
				return 0L;
			}

			LocalDate secondDate = LocalDate.parse(bills.getDueDate(), DateTimeFormatter.ISO_DATE);
			LocalDate now = LocalDate.now();
			return ChronoUnit.MONTHS.between(secondDate, now);

		} catch (DateTimeParseException e) {
			logger.error("Error parsing due date for leftPeriod: {}", e.getMessage());
			return 0L;
		}
	}

	public String typeProduct(Bills bills) {
		if (bills.getProduct() == null || bills.getProduct().length() < 2) {
			return "";
		}
		logger.info("Product {}", bills.getProduct().substring(bills.getProduct().length() - 2));
		return bills.getProduct().substring(bills.getProduct().length() - 2);
	}

	public boolean isTimePeriod(Bills bills) {
		try {
			if (bills.getDueDate() == null) {
				return false;
			}

			LocalDate secondDate = LocalDate.parse(bills.getDueDate(), DateTimeFormatter.ISO_DATE);
			LocalDate now = LocalDate.now();
			return ChronoUnit.MONTHS.between(now, secondDate) == 0;

		} catch (DateTimeParseException e) {
			logger.error("Error parsing due date for isTimePeriod: {}", e.getMessage());
			return false;
		}
	}

	public int diagonalMultiplier(Bills bills) {
		if (!isTimePeriod(bills)) {
			return 1;
		}
		return 0;
	}

	public int flatMultiplier(Bills bills) {
		logger.info("Flat multiplier: {} Is Day", isTimePeriod(bills));
		if (isTimePeriod(bills)) {
			return 0;
		}
		if (timePeriod(bills) > 12) {
			if (payPeriod(bills) > 12) {
				long leftPeriod = leftPeriod(bills);
				return leftPeriod < 3 ? 3 : (int) leftPeriod;
			}
			long leftPeriod = leftPeriod(bills);
			return leftPeriod < 6 ? 6 : (int) leftPeriod;
		} else {
			if (payPeriod(bills) < 6) {
				long leftPeriod = leftPeriod(bills);
				return leftPeriod < 3 ? 3 : (int) leftPeriod;
			}
			return 1;
		}
	}

	public double fixedMonth(Bills bills) {
		if (isTimePeriod(bills) && dayBeetween(bills) > 0) {
			return 0;
		} else if (isTimePeriod(bills) && dayBeetween(bills) < 0) {
			long daysBetween = dayBeetween(bills);
			return daysBetween != 0 ? (double) 30 / daysBetween : 0;
		}
		return 0;
	}

	public long dayBeetween(Bills bills) {
		try {
			if (bills.getDueDate() == null) {
				return 0L;
			}

			LocalDate secondDate = LocalDate.parse(bills.getDueDate(), DateTimeFormatter.ISO_DATE);
			LocalDate now = LocalDate.now();
			// PRESERVE ORIGINAL LOGIC: between now and secondDate
			return ChronoUnit.DAYS.between(now, secondDate);

		} catch (DateTimeParseException e) {
			logger.error("Error parsing due date for dayBeetween: {}", e.getMessage());
			return 0L;
		}
	}
}