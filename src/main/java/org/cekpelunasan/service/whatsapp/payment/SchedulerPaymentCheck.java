package org.cekpelunasan.service.whatsapp.payment;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.dto.InvoiceResponse;
import org.cekpelunasan.entity.Payment;
import org.cekpelunasan.service.payment.PaymentService;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SchedulerPaymentCheck {

	private static final Logger log = LoggerFactory.getLogger(SchedulerPaymentCheck.class);
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
				body.put("phone", payment.getUser() + "@s.whatsapp.net");
				body.put("message", String.format(
					"""
						🎉 *PEMBAYARAN BERHASIL!* 🎉
						
						✅ *Status:* Terverifikasi & Diterima
						💰 *Jumlah:* %s
						🆔 *Invoice:* `%s`
						📅 *Waktu:* %s
						
						🙏 *Terima kasih atas pembayaran Anda!*
						Transaksi Anda telah berhasil diproses.
						
						📱 Simpan pesan ini sebagai referensi""",
					rupiahFormatUtils.formatRupiah(payment.getAmount()),
					payment.getId(),
					LocalDateTime.now().plusHours(7).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
				));
				HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, notification);
				paymentService.deletePayment(payment.getId());
				try {
					log.info("Sending request to WhatsApp gateway: {}", whatsappGatewayurl);

					ResponseEntity<Void> response = restTemplateBuilder.build()
						.postForEntity(whatsappGatewayurl, requestEntity, Void.class);

					if (response.getStatusCode().is2xxSuccessful()) {
						log.info("WhatsApp message sent successfully. Status: {}", response.getStatusCode());
					} else {
						log.warn("WhatsApp request completed but with non-success status: {}", response.getStatusCode());
					}

				} catch (HttpClientErrorException e) {
					log.error("Client error when sending WhatsApp message. Status: {}, Response: {}",
						e.getStatusCode(), e.getResponseBodyAsString());

				} catch (HttpServerErrorException e) {
					log.error("Server error when sending WhatsApp message. Status: {}, Response: {}",
						e.getStatusCode(), e.getResponseBodyAsString());

				} catch (ResourceAccessException e) {
					log.error("Network error when sending WhatsApp message: {}", e.getMessage());

				} catch (Exception e) {
					log.error("Unexpected error when sending WhatsApp message: ", e);
				}

			}
		});
	}
}
