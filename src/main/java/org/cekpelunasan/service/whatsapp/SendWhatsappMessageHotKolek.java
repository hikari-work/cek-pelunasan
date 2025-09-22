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
		// Konfigurasi untuk setiap kategori
		List<KiosConfig> kiosConfigs = List.of(
			new KiosConfig("1075", ""),     // Empty kios -> 1075
			new KiosConfig("1075", "KLJ"),  // KLJ kios -> 1172
			new KiosConfig("1075", "KJB")   // KJB kios -> 1173
		);

		// Menjalankan semua operasi secara paralel
		List<CompletableFuture<BillsData>> futures = kiosConfigs.stream()
			.map(this::fetchBillsDataAsync)
			.toList();

		// Menunggu semua operasi selesai dan mengumpulkan hasilnya
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
			.thenApply(v -> {
				List<BillsData> results = futures.stream()
					.map(CompletableFuture::join)
					.toList();

				// Ekstrak data sesuai urutan untuk menjaga kompatibilitas
				BillsData data1075 = results.get(0);  // Empty kios
				BillsData data1172 = results.get(1);  // KLJ kios
				BillsData data1173 = results.get(2);  // KJB kios

				return generateMessageText.generateMessageText(
					data1075.minimalPay(), data1075.firstPay(), data1075.dueDate(),
					data1172.minimalPay(), data1172.firstPay(), data1172.dueDate(),
					data1173.minimalPay(), data1173.firstPay(), data1173.dueDate()
				);
			})
			.join();
	}

	private CompletableFuture<BillsData> fetchBillsDataAsync(KiosConfig config) {
		String code = config.code();
		String kiosFilter = config.kiosFilter();

		// Menjalankan 3 operasi secara paralel untuk setiap kios
		CompletableFuture<List<Bills>> minimalPay = CompletableFuture
			.supplyAsync(() -> filterBills(hotKolekService.findMinimalPay(code), kiosFilter));

		CompletableFuture<List<Bills>> firstPay = CompletableFuture
			.supplyAsync(() -> filterBills(hotKolekService.findFirstPay(code), kiosFilter));

		CompletableFuture<List<Bills>> dueDate = CompletableFuture
			.supplyAsync(() -> filterBills(hotKolekService.findDueDate(code), kiosFilter));

		// Menggabungkan hasil dari 3 operasi
		return CompletableFuture.allOf(minimalPay, firstPay, dueDate)
			.thenApply(v -> new BillsData(
				minimalPay.join(),
				firstPay.join(),
				dueDate.join()
			));
	}

	private List<Bills> filterBills(List<Bills> bills, String kiosFilter) {
		return bills.stream()
			.filter(bill -> kiosFilter.isEmpty() ?
				bill.getKios().isEmpty() :
				bill.getKios().equals(kiosFilter))
			.toList();
	}

	// Helper classes
	private record KiosConfig(String code, String kiosFilter) {}

	private record BillsData(
		List<Bills> minimalPay,
		List<Bills> firstPay,
		List<Bills> dueDate
	) {}
}
