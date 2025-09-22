package org.cekpelunasan.service.whatsapp;

import org.cekpelunasan.dto.WhatsappMessageDTO;
import org.cekpelunasan.entity.Bills;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
	private String getPreferredAddress(String text) {
		String preferred = "6285227941810-1603156359@g.us";
    	if (text.contains(preferred)) {
        	return preferred;
    	}
		Pattern groupPattern = Pattern.compile("\\b\\d+@g\\.us\\b");
		Matcher groupMatcher = groupPattern.matcher(text);
		if (groupMatcher.find()) {
			return groupMatcher.group();
		}

		Pattern userPattern = Pattern.compile("(?<![:\\d])\\d{7,}@s\\.whatsapp\\.net");
		Matcher userMatcher = userPattern.matcher(text);
		if (userMatcher.find()) {
			return userMatcher.group();
		}

		return null;
	}


	public void sendMessage(WhatsappMessageDTO messageDTO) {
		log.info("Message From {} is Valid", messageDTO.getFrom());
		String target = getPreferredAddress(messageDTO.getFrom());
		if (target == null) {
			log.info("Target User is not Valid");
			return;
		}
		log.info("Target User is {}", target);
		List<String> strings = extractSpkNumbers(messageDTO.getMessage().getText());
		log.info("Saving data {}", strings);
		hotKolekService.saveAllPaying(strings);
		log.info("Data Saved");
		String messageText = generateNonBlocking();

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
	private List<String> extractSpkNumbers(String text) {
		List<String> result = new ArrayList<>();

		String[] tokens = text.trim().split("\\s+");
		for (String token : tokens) {
			String cleanToken = token.startsWith(".") ? token.substring(1) : token;
			if (cleanToken.matches("^\\d{12}$")) {
				result.add(cleanToken);
			}
		}
		return result;
	}

	public String generateNonBlocking() {
		CompletableFuture<List<Bills>> minimalPay1075 = CompletableFuture.supplyAsync(() -> hotKolekService.findMinimalPay("1075")
			.stream().filter(kios -> kios.getKios().isEmpty()).toList());
		CompletableFuture<List<Bills>> firstPay1075 = CompletableFuture.supplyAsync(() -> hotKolekService.findFirstPay("1075")
			.stream().filter(kios -> kios.getKios().isEmpty()).toList());
		CompletableFuture<List<Bills>> dueDate1075 = CompletableFuture.supplyAsync(() -> hotKolekService.findDueDate("1075")
			.stream().filter(kios -> kios.getKios().isEmpty()).toList());

		CompletableFuture<List<Bills>> minimalPay1172 = CompletableFuture.supplyAsync(() -> hotKolekService.findMinimalPay("1075")
			.stream().filter(kios -> kios.getKios().equals("KLJ")).toList());
		CompletableFuture<List<Bills>> firstPay1172 = CompletableFuture.supplyAsync(() -> hotKolekService.findFirstPay("1075")
			.stream().filter(kios -> kios.getKios().equals("KLJ")).toList());
		CompletableFuture<List<Bills>> dueDate1172 = CompletableFuture.supplyAsync(() -> hotKolekService.findDueDate("1075")
			.stream().filter(kios -> kios.getKios().equals("KLJ")).toList());

		CompletableFuture<List<Bills>> minimalPay1173 = CompletableFuture.supplyAsync(() -> hotKolekService.findMinimalPay("1075")
			.stream().filter(kios -> kios.getKios().equals("KJB")).toList());
		CompletableFuture<List<Bills>> firstPay1173 = CompletableFuture.supplyAsync(() -> hotKolekService.findFirstPay("1075")
			.stream().filter(kios -> kios.getKios().equals("KJB")).toList());
		CompletableFuture<List<Bills>> dueDate1173 = CompletableFuture.supplyAsync(() -> hotKolekService.findDueDate("1075")
			.stream().filter(kios -> kios.getKios().equals("KJB")).toList());

		return CompletableFuture.allOf(
			minimalPay1075, firstPay1075, dueDate1075,
			minimalPay1172, firstPay1172, dueDate1172,
			minimalPay1173, firstPay1173, dueDate1173
		).thenApply(v -> {
			try {
				return generateMessageText.generateMessageText(
					minimalPay1075.get(), firstPay1075.get(), dueDate1075.get(),
					minimalPay1172.get(), firstPay1172.get(), dueDate1172.get(),
					minimalPay1173.get(), firstPay1173.get(), dueDate1173.get()
				);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).join();
	}
}
