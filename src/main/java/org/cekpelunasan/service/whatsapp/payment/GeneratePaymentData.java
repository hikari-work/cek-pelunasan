package org.cekpelunasan.service.whatsapp.payment;

import org.cekpelunasan.dto.InvoiceRequest;
import org.cekpelunasan.dto.InvoiceResponse;
import org.cekpelunasan.entity.Payment;
import org.cekpelunasan.service.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
public class GeneratePaymentData {

	private static final Logger log = LoggerFactory.getLogger(GeneratePaymentData.class);
	private final RestTemplateBuilder restTemplateBuilder;
	private final PaymentService paymentService;

	@Value("${payment.url}")
	private String paymentUrl;

	@Value("${payment.auth}")
	private String paymentAuth;

	public GeneratePaymentData(RestTemplateBuilder restTemplateBuilder, PaymentService paymentService1) {
		this.restTemplateBuilder = restTemplateBuilder;
		this.paymentService = paymentService1;
	}


	public InvoiceResponse generateInvoice(Long amount, String pushname) {
		InvoiceRequest invoiceRequest = InvoiceRequest.builder()
			.amount(amount)
			.notes("Pembayaran dari " + pushname)
			.expiresAt(600L)
			.build();

		log.info("Invoice Data Is Created");

		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + paymentAuth);
		headers.set("Content-Type", "application/json");

		HttpEntity<InvoiceRequest> requestHttpEntity = new HttpEntity<>(invoiceRequest, headers);

		try {
			ResponseEntity<InvoiceResponse> invoiceResponseResponseEntity = restTemplateBuilder.build()
				.postForEntity(paymentUrl + "/api/v2/invoices/create", requestHttpEntity, InvoiceResponse.class);

			log.info("Invoice Response {}", invoiceResponseResponseEntity.getBody());
			return invoiceResponseResponseEntity.getBody();
		} catch (Exception e) {
			log.error("Error creating invoice: ", e);
			throw e;
		}
	}

	public byte[] generateQrCode(InvoiceResponse response) {
		String url = String.format("%s/api/v2/invoices/qris/%s", paymentUrl, response.getInvoiceId());
		log.info("Requesting QR Code from URL: {}", url);

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(paymentAuth);
		headers.set("Accept", "image/png, image/jpeg, image/jpg, application/octet-stream"); // Accept image formats

		HttpEntity<String> httpEntity = new HttpEntity<>(headers);

		try {
			ResponseEntity<byte[]> exchange = restTemplateBuilder.build().exchange(
				url,
				HttpMethod.GET,
				httpEntity,
				byte[].class
			);

			log.info("QR Code response status: {}", exchange.getStatusCode());
			log.info("QR Code response headers: {}", exchange.getHeaders());

			if (exchange.getStatusCode().is2xxSuccessful() && exchange.getBody() != null) {
				log.info("QR Code generated successfully, size: {} bytes", exchange.getBody().length);
				return exchange.getBody();
			} else {
				log.error("Failed to get QR Code. Status: {}, Body length: {}",
					exchange.getStatusCode(),
					exchange.getBody() != null ? exchange.getBody().length : 0);
				return new byte[0];
			}

		} catch (Exception e) {
			log.error("Error generating QR Code for invoice {}: ", response.getInvoiceId(), e);
			return new byte[0];
		}
	}
	public void savePaymentData(InvoiceResponse response, String user) {
		paymentService.savePayment(Payment.builder()
				.amount(response.getAmount())
				.id(response.getInvoiceId())
				.isPaid(false)
				.user(user)
			.build());
	}

}
