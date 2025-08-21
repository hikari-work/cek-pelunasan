package org.cekpelunasan.service.payment;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.Payment;
import org.cekpelunasan.repository.PaymentRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {
	private final PaymentRepo paymentRepo;

	public void savePayment(Payment payment)  {
		paymentRepo.save(payment);
	}
	public List<Payment> getAllPayment() {
		return paymentRepo.findAll().stream().filter(payment -> !payment.isPaid()).toList();
	}
	public void deletePayment(String id) {
		paymentRepo.deleteById(id);
	}
}
