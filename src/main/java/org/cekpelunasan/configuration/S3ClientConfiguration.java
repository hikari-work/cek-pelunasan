package org.cekpelunasan.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.List;

/**
 * Mengelola koneksi ke Cloudflare R2 sebagai tempat penyimpanan file.
 * <p>
 * Cloudflare R2 kompatibel dengan protokol S3, jadi kita pakai AWS SDK for Java
 * tapi arahkan endpoint-nya ke R2 milik Cloudflare — bukan ke AWS sungguhan.
 * Class ini menyediakan bean {@link S3AsyncClient} yang bisa di-inject ke mana saja,
 * plus dua method utilitas untuk mengambil file dan mendaftar objek berdasarkan prefix.
 * </p>
 * <p>
 * Konfigurasi yang dibutuhkan di {@code application.properties} atau environment variable:
 * <ul>
 *   <li>{@code r2.access.key} — access key R2</li>
 *   <li>{@code r2.secret.key} — secret key R2</li>
 *   <li>{@code r2.account.id} — account ID Cloudflare</li>
 *   <li>{@code r2.endpoint} — endpoint R2 (bisa URL lengkap atau suffix saja)</li>
 *   <li>{@code r2.bucket} — nama bucket yang dipakai</li>
 * </ul>
 * </p>
 */
@Configuration
public class S3ClientConfiguration{

	private static final Logger log = LoggerFactory.getLogger(S3ClientConfiguration.class);

	@Value("${r2.access.key}")
	private String accessKey;

	@Value("${r2.account.id}")
	private String accountId;

	@Value("${r2.secret.key}")
	private String secretKey;

	@Value("${r2.endpoint}")
	private String endpoint;

	@Value("${r2.bucket}")
	private String bucket;

	/**
	 * Membuat dan mendaftarkan {@link S3AsyncClient} yang sudah dikonfigurasi untuk Cloudflare R2.
	 * <p>
	 * Path-style access diaktifkan karena R2 tidak mendukung virtual-hosted-style seperti AWS S3.
	 * Region dikunci ke {@code US_EAST_1} karena itu yang diharapkan SDK meski R2 tidak peduli
	 * dengan nilai region-nya.
	 * </p>
	 *
	 * @return client S3 async yang siap dipakai untuk operasi baca/tulis ke R2
	 */
	@Bean
	public S3AsyncClient s3AsyncClient() {
		String endpointUrl = endpoint.startsWith("http") ? endpoint : buildEndpointUrl();
		log.info("Initializing S3AsyncClient with endpoint: {}", endpointUrl);
		return S3AsyncClient.builder()
			.endpointOverride(URI.create(endpointUrl))
			.credentialsProvider(createCredentialsProvider())
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
			.region(Region.US_EAST_1)
			.build();
	}

	/**
	 * Merakit URL endpoint R2 lengkap dari account ID dan suffix endpoint yang dikonfigurasi.
	 * <p>
	 * Dipanggil hanya jika nilai {@code r2.endpoint} belum dimulai dengan "http".
	 * Contoh hasil: {@code https://abc123.r2.cloudflarestorage.com}
	 * </p>
	 *
	 * @return URL endpoint lengkap dengan protokol HTTPS
	 */
	private String buildEndpointUrl() {
		return "https://" + accountId + "." + endpoint;
	}

	/**
	 * Membuat provider kredensial statis dari access key dan secret key yang sudah dikonfigurasi.
	 *
	 * @return {@link StaticCredentialsProvider} berisi kredensial R2
	 */
	private StaticCredentialsProvider createCredentialsProvider() {
		return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
	}

	/**
	 * Mengunduh satu file dari R2 berdasarkan key-nya dan mengembalikan isinya sebagai array byte.
	 * <p>
	 * Operasi ini non-blocking — hasilnya dibungkus dalam {@link Mono} sehingga bisa
	 * dirantai dengan operasi reaktif lainnya. Jika file tidak ditemukan atau terjadi error,
	 * method ini mengembalikan {@code Mono.empty()} dan mencatat error ke log.
	 * </p>
	 *
	 * @param key path/nama file di dalam bucket, misalnya {@code "dokumen/ktp_nasabah.pdf"}
	 * @return {@link Mono} berisi byte array isi file, atau kosong jika gagal
	 */
	public Mono<byte[]> getFile(String key) {
		try {
			GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
			return Mono.fromFuture(s3AsyncClient().getObject(request, AsyncResponseTransformer.toBytes()))
				.map(BytesWrapper::asByteArray)
				.onErrorResume(e -> {
					log.error("Failed to download file from S3: {}", e.getMessage());
					return Mono.empty();
				});
		} catch (Exception e) {
			log.error("S3 getFile setup error for key {}: {}", key, e.getMessage());
			return Mono.empty();
		}
	}

	/**
	 * Memeriksa apakah objek S3 sudah pernah dinotifikasi, dengan mengecek tag {@code notified=true}.
	 *
	 * @param key path/nama file di dalam bucket
	 * @return {@link Mono} berisi {@code true} jika sudah dinotifikasi, {@code false} jika belum atau error
	 */
	public Mono<Boolean> isAlreadyNotified(String key) {
		GetObjectTaggingRequest request = GetObjectTaggingRequest.builder()
			.bucket(bucket)
			.key(key)
			.build();
		return Mono.fromFuture(s3AsyncClient().getObjectTagging(request))
			.map(response -> response.tagSet().stream()
				.anyMatch(tag -> "notified".equals(tag.key()) && "true".equals(tag.value())))
			.onErrorResume(e -> {
				log.warn("Gagal membaca tag untuk {}: {}", key, e.getMessage());
				return Mono.just(false);
			});
	}

	/**
	 * Menandai objek S3 dengan tag {@code notified=true} sebagai tanda sudah dinotifikasi.
	 * <p>
	 * Tag ini bersifat persisten di R2 sehingga tetap ada meski aplikasi restart.
	 * Jika objek sudah memiliki tag lain, tag tersebut akan dipertahankan.
	 * </p>
	 *
	 * @param key path/nama file di dalam bucket yang akan di-tag
	 * @return {@link Mono} yang selesai setelah tag berhasil disimpan, atau empty jika error
	 */
	public Mono<Void> tagObjectAsNotified(String key) {
		GetObjectTaggingRequest getRequest = GetObjectTaggingRequest.builder()
			.bucket(bucket)
			.key(key)
			.build();
		return Mono.fromFuture(s3AsyncClient().getObjectTagging(getRequest))
			.flatMap(existing -> {
				List<Tag> tags = new java.util.ArrayList<>(existing.tagSet());
				tags.removeIf(t -> "notified".equals(t.key()));
				tags.add(Tag.builder().key("notified").value("true").build());
				PutObjectTaggingRequest putRequest = PutObjectTaggingRequest.builder()
					.bucket(bucket)
					.key(key)
					.tagging(Tagging.builder().tagSet(tags).build())
					.build();
				return Mono.fromFuture(s3AsyncClient().putObjectTagging(putRequest));
			})
			.then()
			.onErrorResume(e -> {
				log.error("Gagal meng-tag objek {} sebagai notified: {}", key, e.getMessage());
				return Mono.empty();
			});
	}

	/**
	 * Mendaftar semua key (nama file) di bucket yang diawali dengan prefix tertentu.
	 * <p>
	 * R2 mendukung pagination untuk listing besar — method ini secara otomatis
	 * mengikuti token paginasi sampai semua hasil terkumpul, lalu memancarkan
	 * setiap key satu per satu lewat {@link Flux}.
	 * </p>
	 * <p>
	 * Contoh penggunaan: cari semua file milik nasabah dengan nomor rekening tertentu
	 * menggunakan prefix {@code "nasabah/001234/"}.
	 * </p>
	 *
	 * @param prefix awalan path yang digunakan untuk menyaring objek, bisa berupa folder virtual
	 * @return {@link Flux} yang memancarkan key setiap objek yang cocok, atau kosong jika error
	 */
	public Flux<String> listObjectFoundByName(String prefix) {
		try {
			ListObjectsV2Request initialRequest = ListObjectsV2Request.builder()
				.bucket(bucket)
				.prefix(prefix)
				.build();
			return Mono.fromFuture(s3AsyncClient().listObjectsV2(initialRequest))
				.expand(response -> response.isTruncated()
					? Mono.fromFuture(s3AsyncClient().listObjectsV2(
						ListObjectsV2Request.builder()
							.bucket(bucket)
							.prefix(prefix)
							.continuationToken(response.nextContinuationToken())
							.build()))
					: Mono.empty())
				.flatMap(response -> Flux.fromIterable(response.contents()))
				.map(S3Object::key)
				.onErrorResume(e -> {
					log.error("Failed to list S3 objects with prefix {}: {}", prefix, e.getMessage());
					return Flux.empty();
				});
		} catch (Exception e) {
			log.error("S3 listObjectFoundByName setup error for prefix {}: {}", prefix, e.getMessage());
			return Flux.empty();
		}
	}
}
