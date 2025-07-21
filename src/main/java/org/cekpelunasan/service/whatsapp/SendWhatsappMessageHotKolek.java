package org.cekpelunasan.service.whatsapp;

import org.cekpelunasan.dto.WhatsappMessageDTO;
import org.cekpelunasan.service.Bill.HotKolekService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SendWhatsappMessageHotKolek {

	private static final Logger log = LoggerFactory.getLogger(SendWhatsappMessageHotKolek.class);
	@Value("${whatsapp.gateway.username}")
	private String username;

	@Value("${whatsapp.gateway.password}")
	private String password;

	@Value("${whatsapp.gateway.url}")
	private String url;

	private final GenerateMessageText generateMessageText;
	private final HotKolekService hotKolekService;
	RestTemplate restTemplate = new RestTemplate();

	public SendWhatsappMessageHotKolek(GenerateMessageText generateMessageText, HotKolekService hotKolekService) {
		this.generateMessageText = generateMessageText;
		this.hotKolekService = hotKolekService;
	}

	private boolean isValidSpk(String text) {
		return text.matches("^\\d{12}$");
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

	public void sendMessage(WhatsappMessageDTO messageDTO) {
		String spk = messageDTO.getMessage().getText().replace(".", "");
		if (!isValidSpk(spk)) {
			return;
		}
		log.info("Message From {} is Valid", messageDTO.getFrom());
		String target = getValidUser(messageDTO);
		if (target == null) {
			log.info("Target User is not Valid");
			return;
		}
		log.info("Target User is {}", target);
		log.info("Saving Data {}", spk);
		hotKolekService.savePaying(spk);
		log.info("Data Saved");
		String messageText = generateMessageText.generateMessageText(
			hotKolekService.findMinimalPay("1075"), hotKolekService.findFirstPay("1075"), hotKolekService.findDueDate("1075"),
			hotKolekService.findMinimalPay("1172"), hotKolekService.findFirstPay("1172"), hotKolekService.findDueDate("1172"),
			hotKolekService.findMinimalPay("1173"), hotKolekService.findFirstPay("1173"), hotKolekService.findDueDate("1173")
		);

		String jsonBody = String.format(
			"{\"phone\":\"%s\",\"message\":\"%s\",\"is_forwarded\":false}",
			target,
			escapeJsonString(messageText)
		);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		String auth = username + ":" + password;
		byte[] encode = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
		String authHeader = "Basic " + new String(encode);
		headers.set("Authorization", authHeader);

		HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
		ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
		log.info("Response: {}", response.getBody());

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


}