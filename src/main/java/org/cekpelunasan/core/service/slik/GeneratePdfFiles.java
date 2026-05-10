package org.cekpelunasan.core.service.slik;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Menghasilkan file PDF laporan SLIK dengan tampilan yang rapi dan sesuai
 * format perusahaan. Proses pembuatan PDF terdiri dari tiga tahap:
 * <ol>
 *   <li>Mengirim data mentah SLIK ke endpoint PHP yang menghasilkan HTML laporan</li>
 *   <li>Memanipulasi HTML tersebut: hapus script, pasang logo, rapikan tabel tanda tangan</li>
 *   <li>Render HTML menjadi PDF menggunakan OpenHTMLtoPDF (pure Java, tanpa browser)</li>
 * </ol>
 */
@Component
public class GeneratePdfFiles {

	private static final Logger logger = LoggerFactory.getLogger(GeneratePdfFiles.class);
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

	@Value("${pdf.endpoint.url:https://kredit.suryayudha.id/ideb/generate.php}")
	private String pdfEndpointUrl;

	@Value("${pdf.logo.url:https://kredit.suryayudha.id/ideb/logo.png}")
	private String logoUrl;

	private final WebClient webClient;

	public GeneratePdfFiles(WebClient whatsappWebClient) {
		this.webClient = whatsappWebClient;
	}

	/**
	 * Pipeline reaktif lengkap: ambil HTML dari endpoint → parse dan manipulasi
	 * → render jadi PDF. Jika salah satu tahap gagal atau menghasilkan konten
	 * kosong, proses berhenti dan {@link Mono#empty()} dikembalikan.
	 *
	 * @param pdfBytes       byte array data SLIK mentah yang dikirim ke endpoint PHP
	 * @param fasilitasAktif {@code true} jika hanya fasilitas aktif yang ingin ditampilkan
	 * @return {@link Mono} berisi byte array PDF yang sudah dirender, atau kosong jika gagal
	 */
	public Mono<byte[]> generatePdf(byte[] pdfBytes, boolean fasilitasAktif) {
		if (pdfBytes == null || pdfBytes.length == 0) {
			logger.warn("PDF bytes is null or empty");
			return Mono.empty();
		}
		return fetchHtmlFromEndpoint(pdfBytes, fasilitasAktif)
			.flatMap(this::parseAndManipulateHtml)
			.flatMap(this::renderPdfWithOpenHtml);
	}

	/**
	 * Mengirim data SLIK ke endpoint PHP menggunakan HTTP POST multipart.
	 * Data dikirim sebagai file dengan nama "ideb.txt" beserta parameter
	 * tambahan untuk mengontrol tampilan fasilitas aktif/semua.
	 *
	 * @param body           byte array data yang dikirim sebagai file
	 * @param fasilitasAktif {@code true} untuk mengirim parameter "y", {@code false} untuk "n"
	 * @return {@link Mono} berisi HTML yang dikembalikan oleh endpoint, atau kosong jika gagal
	 */
	private Mono<String> fetchHtmlFromEndpoint(byte[] body, boolean fasilitasAktif) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("fileToUpload", body)
			.filename("ideb.txt")
			.contentType(MediaType.TEXT_PLAIN);
		builder.part("fasilitasAktif", fasilitasAktif ? "y" : "n");

		return webClient.post()
			.uri(pdfEndpointUrl)
			.header("User-Agent", USER_AGENT)
			.body(BodyInserters.fromMultipartData(builder.build()))
			.retrieve()
			.bodyToMono(String.class)
			.onErrorResume(e -> {
				logger.error("Error fetching HTML from PDF endpoint: {}", e.getMessage());
				return Mono.empty();
			});
	}

	/**
	 * Memparsing HTML menggunakan Jsoup lalu melakukan serangkaian manipulasi
	 * agar tampilan PDF lebih baik: menghapus script, memasang logo perusahaan,
	 * menghapus tombol cetak, dan memperbaiki layout tabel tanda tangan.
	 *
	 * @param htmlContent string HTML yang didapat dari endpoint PHP
	 * @return {@link Mono} berisi dokumen Jsoup yang sudah dimanipulasi, atau kosong jika HTML kosong
	 */
	private Mono<Document> parseAndManipulateHtml(String htmlContent) {
		if (htmlContent == null || htmlContent.isBlank()) {
			logger.warn("HTML content is null or blank");
			return Mono.empty();
		}
		return Mono.fromCallable(() -> {
			Document document = Jsoup.parse(htmlContent);
			removeScriptTag(document);
			insertingImages(document);
			removePrintButtons(document);
			fixSignatureGrid(document);
			return document;
		}).subscribeOn(Schedulers.boundedElastic());
	}

	/**
	 * Merender dokumen Jsoup menjadi file PDF menggunakan OpenHTMLtoPDF.
	 * Ukuran halaman A4 landscape dengan margin 15mm diinjeksikan lewat CSS
	 * {@code @page} sebelum rendering. Seluruh operasi berjalan di thread
	 * {@code boundedElastic} karena OpenHTMLtoPDF bersifat blocking.
	 *
	 * @param doc dokumen Jsoup yang sudah dimanipulasi
	 * @return {@link Mono} berisi byte array PDF yang sudah dirender
	 */
	private Mono<byte[]> renderPdfWithOpenHtml(Document doc) {
		return Mono.fromCallable(() -> {
			doc.head().appendElement("style")
				.text("@page { size: A4 landscape; margin: 15mm; }");

			// OpenHTMLtoPDF butuh XHTML (well-formed XML): set output ke XML syntax
			// agar Jsoup menghasilkan tag self-closing (<br/>, <img/>) dan menutup semua tag.
			doc.outputSettings()
				.syntax(Document.OutputSettings.Syntax.xml)
				.charset(java.nio.charset.StandardCharsets.UTF_8)
				.escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);

			try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				PdfRendererBuilder builder = new PdfRendererBuilder();
				builder.useFastMode();
				builder.withHtmlContent(doc.outerHtml(), null);
				builder.toStream(baos);
				builder.run();
				return baos.toByteArray();
			}
		})
		.doOnError(e -> logger.error("Gagal render PDF: {}", e.getMessage()))
		.onErrorResume(e -> Mono.empty())
		.subscribeOn(Schedulers.boundedElastic());
	}

	/**
	 * Menghapus semua tag {@code <script>} dari dokumen HTML agar PDF
	 * tidak berisi kode JavaScript yang tidak diperlukan.
	 *
	 * @param doc dokumen HTML yang akan dibersihkan
	 */
	private void removeScriptTag(Document doc) {
		doc.getElementsByTag("script").remove();
	}

	/**
	 * Mengatur ulang header dokumen HTML agar logo perusahaan tampil di
	 * pojok kanan atas dan judul laporan di sebelah kiri, menggunakan
	 * tabel HTML dua kolom yang lebih stabil untuk rendering PDF
	 * dibanding layout flexbox.
	 *
	 * @param document dokumen HTML yang akan dimodifikasi
	 */
	private void insertingImages(Document document) {
		Element image = document.selectFirst("img.right-image");
		if (image == null) {
			return;
		}
		image.attr("src", logoUrl);
		image.removeAttr("style");
		image.attr("style", "width: 160px;");

		Element headerTable = new Element("table");
		headerTable.attr("style", "width: 100%; border: none; margin-bottom: 20px; border-collapse: collapse;");

		Element row = new Element("tr");
		headerTable.appendChild(row);

		Element tdLeft = new Element("td");
		tdLeft.attr("style", "border: none; vertical-align: middle; text-align: left;");
		document.select("h3").forEach(h3 -> {
			h3.attr("style", "margin: 2px 0; font-family: sans-serif;");
			tdLeft.appendChild(h3);
		});
		row.appendChild(tdLeft);

		Element tdRight = new Element("td");
		tdRight.attr("style",
			"border: none; vertical-align: middle; text-align: right; width: 1%; white-space: nowrap;");
		tdRight.appendChild(image);
		row.appendChild(tdRight);

		document.body().prependChild(headerTable);
		logger.debug("Header layout converted to Table for PDF stability");
	}

	/**
	 * Menghapus div tombol cetak dari HTML agar tidak ikut muncul di PDF.
	 * Tombol cetak dikenali dari selector {@code div.text-right} yang berisi
	 * elemen {@code button#print}.
	 *
	 * @param doc dokumen HTML yang akan dibersihkan
	 */
	private void removePrintButtons(Document doc) {
		doc.select("div.text-right").forEach(element -> {
			if (!element.select("button#print").isEmpty()) {
				element.remove();
			}
		});
	}

	/**
	 * Mengonversi layout CSS Grid pada area tanda tangan menjadi tabel HTML
	 * biasa. CSS Grid tidak dirender dengan baik oleh renderer PDF berbasis Java,
	 * sehingga perlu diganti dengan tabel dua baris: baris pertama berisi
	 * label kolom (bold biru), baris kedua berisi ruang tanda tangan (kosong).
	 *
	 * <p>Setelah konversi, div grid asli dihapus dan tabel baru ditambahkan ke
	 * area printable. Div flex parent yang hanya punya satu anak juga diperbaiki
	 * stylenya agar tidak memengaruhi layout sekitarnya.</p>
	 *
	 * @param doc dokumen HTML yang akan diperbaiki
	 */
	private void fixSignatureGrid(Document doc) {
		Element gridDiv = doc.selectFirst("div[style*='display: grid']");

		if (gridDiv != null) {
			List<Element> children = new ArrayList<>(gridDiv.children());

			Element table = new Element("table");
			table.attr("style",
				"width: 300px; border-collapse: collapse; font-family: sans-serif; font-size: 12px; border: 0.5px solid blue; margin-top: 50px; page-break-inside: avoid;");

			Element tbody = new Element("tbody");
			table.appendChild(tbody);

			Element row1 = new Element("tr");
			for (int i = 0; i < 3 && i < children.size(); i++) {
				Element originalDiv = children.get(i);
				Element td = new Element("td");
				td.attr("style",
					"border: 0.5px solid blue; font-weight: bold; color: blue; text-align: center; padding: 4px; width: 100px;");
				td.text(originalDiv.text());
				row1.appendChild(td);
			}
			tbody.appendChild(row1);

			Element row2 = new Element("tr");
			for (int i = 3; i < 6 && i < children.size(); i++) {
				Element td = new Element("td");
				td.attr("style", "border: 0.5px solid blue; height: 40px;");
				row2.appendChild(td);
			}
			tbody.appendChild(row2);

			Element container = doc.selectFirst("div.printableArea");
			Objects.requireNonNullElseGet(container, doc::body).appendChild(table);

			gridDiv.remove();

			Element parentFlex = doc.selectFirst("div[style*='display: flex']");
			if (parentFlex != null && parentFlex.childrenSize() <= 1) {
				parentFlex.attr("style", "font-family: sans-serif;");
			}
		}
	}
}
