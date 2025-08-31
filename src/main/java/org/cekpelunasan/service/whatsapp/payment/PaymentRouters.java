package org.cekpelunasan.service.whatsapp.payment;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.dto.InvoiceResponse;
import org.cekpelunasan.dto.WhatsappMessageDTO;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.util.Base64;

@Slf4j
@Component
public class PaymentRouters {

	@Value("${whatsapp.gateway.url}")
	private String whatsappGatewayurl;

	@Value("${whatsapp.gateway.username}")
	private String whatsappUsername;

	@Value("${whatsapp.gateway.password}")
	private String whatsappPassword;

	private final GeneratePaymentData generatePaymentData;
	private final RupiahFormatUtils rupiahFormatUtils;
	private final RestTemplateBuilder restTemplateBuilder;

	public PaymentRouters(GeneratePaymentData generatePaymentData, RupiahFormatUtils rupiahFormatUtils, RestTemplateBuilder restTemplateBuilder) {
		this.generatePaymentData = generatePaymentData;
		this.rupiahFormatUtils = rupiahFormatUtils;
		this.restTemplateBuilder = restTemplateBuilder;
	}

	private HttpHeaders createBasicAuthHeaders() {
		HttpHeaders headers = new HttpHeaders();
		String credentials = whatsappUsername + ":" + whatsappPassword;
		String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
		headers.set("Authorization", "Basic " + encodedCredentials);
		return headers;
	}

	public void generatePayment(WhatsappMessageDTO dto) {
		String[] data = dto.getMessage().getText().split(" ");
		if (data.length != 2) {
			log.warn("Invalid message format. Expected 2 parts, got: {}", data.length);
			return;
		}
		log.info("Data is valid");

		try {
			InvoiceResponse invoiceResponse = generatePaymentData.generateInvoice(Long.parseLong(data[1]), dto.getPushname());
			log.info("Invoice Response {}", invoiceResponse);
			if (invoiceResponse == null) {
				log.error("Invoice response is null");
				return;
			}

			byte[] bytes = generatePaymentData.generateQrCode(invoiceResponse);
			log.info("QR Code bytes length: {}", bytes.length);
			if (bytes.length == 0) {
				log.error("QR Code bytes is empty");
				return;
			}

			ByteArrayResource imageResource = new ByteArrayResource(bytes) {
				@Override
				public String getFilename() {
					return "qrcode.png";
				}
			};

			MultiValueMap<String, Object> request = new LinkedMultiValueMap<>();
			request.add("phone", dto.getChat_id() + "@s.whatsapp.net");
			request.add("caption", String.format(
				"""
					✅ *INVOICE PEMBAYARAN BERHASIL DIBUAT*
					
					📋 *Detail Pembayaran:*
					• Invoice ID: `%s`
					• Jumlah: *%s*
					• Status: 🟡 *Pending*
					
					💳 *Cara Pembayaran:*
					Scan QR Code di atas dengan aplikasi mobile banking atau e-wallet Anda
					
					⏰ *PERHATIAN:*
					Anda memiliki waktu *10 menit* untuk menyelesaikan pembayaran ini
					
					❓ Butuh bantuan? Hubungi customer service kami""",
				invoiceResponse.getInvoiceId(),
				rupiahFormatUtils.formatRupiah(invoiceResponse.getAmount())
			));
			request.add("image", imageResource);
			request.add("compress", true);
			request.add("is_forwarded", false);
			request.add("duration", 600);

			HttpHeaders headers = createBasicAuthHeaders();
			headers.setContentType(MediaType.MULTIPART_FORM_DATA);

			HttpEntity<MultiValueMap<String, Object>> requestHttpEntity = new HttpEntity<>(request, headers);

			String whatsappImageUrl = whatsappGatewayurl.replace("message", "image");
			log.info("Sending WhatsApp image to URL: {}", whatsappImageUrl);
			log.info("Request payload - phone: {}, caption length: {}, photo size: {} bytes",
				dto.getFrom(), request.getFirst("caption").toString().length(), bytes.length);

			ResponseEntity<String> stringResponseEntity = restTemplateBuilder.build()
				.postForEntity(whatsappImageUrl, requestHttpEntity, String.class);

			log.info("WhatsApp API response status: {}", stringResponseEntity.getStatusCode());
			log.info("WhatsApp API response body: {}", stringResponseEntity.getBody());

			if (stringResponseEntity.getStatusCode().is2xxSuccessful()) {
				log.info("WhatsApp message sent successfully, saving payment data");
				generatePaymentData.savePaymentData(invoiceResponse, dto.getChat_id());
			} else {
				log.error("Failed to send WhatsApp message. Status: {}, Body: {}",
					stringResponseEntity.getStatusCode(), stringResponseEntity.getBody());
			}

		} catch (NumberFormatException e) {
			log.error("Invalid number format for amount: {}", data[1], e);
			throw new RuntimeException("Invalid amount format", e);
		} catch (Exception e) {
			log.error("Error in generatePayment: ", e);
			throw new RuntimeException("Failed to generate payment", e);
		}
	}
}
