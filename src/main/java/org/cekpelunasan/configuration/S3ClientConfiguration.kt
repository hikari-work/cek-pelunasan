package org.cekpelunasan.configuration

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import java.net.URI

/**
 * Mengelola koneksi ke Cloudflare R2 sebagai tempat penyimpanan file.
 *
 * Cloudflare R2 kompatibel dengan protokol S3, jadi kita pakai AWS SDK for Java
 * tapi arahkan endpoint-nya ke R2 milik Cloudflare — bukan ke AWS sungguhan.
 * Class ini menyediakan bean [S3AsyncClient] yang bisa di-inject ke mana saja,
 * plus dua method utilitas untuk mengambil file dan mendaftar objek berdasarkan prefix.
 */
@Configuration
class S3ClientConfiguration {

    companion object {
        private val log = LoggerFactory.getLogger(S3ClientConfiguration::class.java)
    }

    @Value("\${r2.access.key}")
    private lateinit var accessKey: String

    @Value("\${r2.account.id}")
    private lateinit var accountId: String

    @Value("\${r2.secret.key}")
    private lateinit var secretKey: String

    @Value("\${r2.endpoint}")
    private lateinit var endpoint: String

    @Value("\${r2.bucket}")
    private lateinit var bucket: String

    @Bean
    fun s3AsyncClient(): S3AsyncClient {
        val endpointUrl = if (endpoint.startsWith("http")) endpoint else "https://$accountId.$endpoint"
        log.info("Initializing S3AsyncClient with endpoint: {}", endpointUrl)
        return S3AsyncClient.builder()
            .endpointOverride(URI.create(endpointUrl))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .region(Region.US_EAST_1)
            .build()
    }

    fun getFile(key: String): Mono<ByteArray> {
        val request = GetObjectRequest.builder().bucket(bucket).key(key).build()
        return Mono.fromFuture(s3AsyncClient().getObject(request, AsyncResponseTransformer.toBytes()))
            .map { it.asByteArray() }
            .onErrorResume { e ->
                log.error("Failed to download file from S3: {}", e.message)
                Mono.empty()
            }
    }

    fun listObjectFoundByName(prefix: String): Flux<String> {
        val initialRequest = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build()
        return Mono.fromFuture(s3AsyncClient().listObjectsV2(initialRequest))
            .expand { response ->
                if (response.isTruncated)
                    Mono.fromFuture(
                        s3AsyncClient().listObjectsV2(
                            ListObjectsV2Request.builder()
                                .bucket(bucket)
                                .prefix(prefix)
                                .continuationToken(response.nextContinuationToken())
                                .build()
                        )
                    )
                else Mono.empty()
            }
            .flatMap { response -> Flux.fromIterable(response.contents()) }
            .map { it.key() }
            .onErrorResume { e ->
                log.error("Failed to list S3 objects with prefix {}: {}", prefix, e.message)
                Flux.empty()
            }
    }
}
