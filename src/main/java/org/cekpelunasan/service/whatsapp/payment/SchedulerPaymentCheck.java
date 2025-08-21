package org.cekpelunasan.service.whatsapp.payment;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.dto.InvoiceResponse;
import org.cekpelunasan.dto.SendMessageResponse;
import org.cekpelunasan.entity.Payment;
import org.cekpelunasan.service.payment.PaymentService;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SchedulerPaymentCheck {

	private final RupiahFormatUtils rupiahFormatUtils;
	@Value("${payment.url}")
	private String paymentUrl;

	@Value("${payment.auth}")
	private String paymentAuth;

	@Value("${whatsapp.gateway.url}")
	private String whatsappGatewayurl;

	@Value("${whatsapp.gateway.password}")
	private String password;

	@Value("${whatsapp.gateway.username}")
	private String username;


	private final PaymentService paymentService;
	private final RestTemplateBuilder restTemplateBuilder;

	@Scheduled(fixedRate = 10 * 1000)
	public void checkPayment() {
		List<Payment> allPayment = paymentService.getAllPayment();
		allPayment.parallelStream().forEach(payment -> {
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(paymentAuth);

			HttpEntity<String> httpEntity = new HttpEntity<>(headers);

			ResponseEntity<InvoiceResponse> forEntity = restTemplateBuilder.build().exchange(
				paymentUrl + "/api/v2/invoices/details/{id}",
				HttpMethod.GET,
				httpEntity,
				InvoiceResponse.class,
				payment.getId()
			);
			if (forEntity.getBody() == null) {
				return;
			}
			if (forEntity.getBody().getStatus().equals("PAID")) {
				HttpHeaders notification = new HttpHeaders();
				notification.setContentType(MediaType.APPLICATION_JSON);
				notification.setBasicAuth(username, password);
				Map<String, Object> body = new HashMap<>();
				body.put("phone", payment.getUser());
				body.put("message", String.format(
					"🎉 *PEMBAYARAN BERHASIL!* 🎉\n\n" +
						"✅ *Status:* Terverifikasi & Diterima\n" +
						"💰 *Jumlah:* %s\n" +
						"🆔 *Invoice:* `%s`\n" +
						"📅 *Waktu:* %s\n\n" +
						"🙏 *Terima kasih atas pembayaran Anda!*\n" +
						"Transaksi Anda telah berhasil diproses.\n\n" +
						"📱 Simpan pesan ini sebagai referensi",
					rupiahFormatUtils.formatRupiah(payment.getAmount()),
					payment.getId(),
					LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
				));
				HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, notification);
				ResponseEntity<SendMessageResponse> sendMessageResponseResponseEntity = restTemplateBuilder.build().postForEntity(whatsappGatewayurl, requestEntity, SendMessageResponse.class);
				if (sendMessageResponseResponseEntity.getStatusCode().is2xxSuccessful()) {
					paymentService.deletePayment(payment.getId());
				}

			}
		});
	}
}
