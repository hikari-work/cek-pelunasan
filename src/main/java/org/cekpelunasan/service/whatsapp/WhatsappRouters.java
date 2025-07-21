package org.cekpelunasan.service.whatsapp;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.dto.WhatsappMessageDTO;
import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.entity.Savings;
import org.cekpelunasan.service.repayment.RepaymentService;
import org.cekpelunasan.service.savings.SavingsService;
import org.cekpelunasan.utils.PenaltyUtils;
import org.cekpelunasan.utils.RepaymentCalculator;
import org.cekpelunasan.utils.SavingsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WhatsappRouters {

	private static final Logger log = LoggerFactory.getLogger(WhatsappRouters.class);
	private final RepaymentService repaymentService;
	private final SavingsService savingsService;
	private final SavingsUtils savingsUtils;
	private final RestTemplate restTemplate = new RestTemplate();
	private final PenaltyUtils penaltyUtils;
	private final RepaymentCalculator repaymentCalculator;
	private final SendWhatsappMessageHotKolek sendWhatsappMessageHotKolek;
	@Value("${whatsapp.gateway.username}")
	private String username;

	@Value("${whatsapp.gateway.password}")
	private String password;

	@Value("${whatsapp.gateway.url}")
	private String url;



	private boolean isValidData(String text) {
		return text.matches("^\\d{12}$");
	}
	private boolean isValidHotKolek(String text) {
		return text.matches("^.\\d{12}$");
	}


	public void sendPelunasanOrTabungan(WhatsappMessageDTO whatsappMessageDTO) {
		String target = getValidUser(whatsappMessageDTO);
		if (target == null) {
			return;
		}
		String text = whatsappMessageDTO.getMessage().getText();
		if (!isValidData(text)) {
			log.info("Not Valid Hot Kolek Service");
			return;
		}
		if (isValidHotKolek(text)) {
			log.info("Hot Kolek Service");
			sendWhatsappMessageHotKolek.sendMessage(whatsappMessageDTO);
			return;
		}
		Repayment repaymentById = repaymentService.findRepaymentById(Long.parseLong(text));
		if (repaymentById == null) {
			Optional<Savings> byId = savingsService.findById(text);
			byId.ifPresent(data -> {
				String savings = savingsUtils.getSavings(data);
				sendMessage(target, savings);
			});
			return;
		}
		Map<String, Long> penalty = penaltyUtils.penalty(
			repaymentById.getStartDate(),
			repaymentById.getPenaltyLoan(),
			repaymentById.getProduct(),
			repaymentById
		);
		String resultRepayment = repaymentCalculator.calculate(repaymentById, penalty);
		sendMessage(target, resultRepayment);

	}
	private String getValidUser(WhatsappMessageDTO whatsappMessageDTO) {
		String target = whatsappMessageDTO.getFrom();
		Pattern pattern = Pattern.compile("(62\\d+@s\\.whatsapp\\.net)");
		Matcher matcher = pattern.matcher(target);
		if (matcher.find()) {
			return matcher.group(1);
		} else {
			return null;
		}
	}
	private String escapeJsonString(String input) {
		if (input == null) {
			return "";
		}
		return input.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
	private void sendMessage(String target, String messageText) {
		String messageTextEscaped = escapeJsonString(messageText);
		String jsonBody = String.format(
			"{\"phone\":\"%s\",\"message\":\"%s\",\"is_forwarded\":false}",
			target,
			messageTextEscaped
		);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		String auth = username + ":" + password;
		byte[] encode = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
		String authHeader = "Basic " + new String(encode);
		headers.set("Authorization", authHeader);

		HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
		log.info("Request: {}", request.getBody());
		ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
		log.info("Response: {}", response.getBody());
	}

}
