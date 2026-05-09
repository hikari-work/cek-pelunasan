---
name: configuration
description: Semua kelas konfigurasi Spring di project cek-pelunasan — TDLight, Playwright, S3/R2, WebClient, Async, dan property yang dibutuhkan
---

# Configuration Classes

Package: `org.cekpelunasan.configuration`

---

## TDLightConfiguration
**File**: `configuration/TDLightConfiguration.java`

**Fungsi**: Setup Telegram TDLib native client.

```java
@Configuration
public class TDLightConfiguration {

    @Bean
    public TelegramClientFactory telegramClientFactory(
        @Value("${telegram.api.id}") int apiId,
        @Value("${telegram.api.hash}") String apiHash,
        @Value("${telegram.session.path}") String sessionPath
    ) {
        // Inisialisasi TDLight dengan API credentials
        // Session disimpan ke file (persistent login)
        return new TelegramClientFactory(apiId, apiHash, sessionPath);
    }
}
```

**Properties yang dibutuhkan**:
```properties
telegram.api.id=12345678
telegram.api.hash=abcdef1234567890abcdef1234567890
telegram.bot.token=123456:ABC-DEF...
telegram.session.path=/app/session
```

---

## PlaywrightConfiguration
**File**: `configuration/PlaywrightConfiguration.java`

**Fungsi**: Setup Playwright headless Chromium untuk PDF generation.

```java
@Configuration
public class PlaywrightConfiguration {

    @Bean
    public Playwright playwright() {
        return Playwright.create();
    }

    @Bean
    public Browser browser(Playwright playwright) {
        return playwright.chromium().launch(
            new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of("--no-sandbox", "--disable-setuid-sandbox"))
        );
    }
}
```

> `--no-sandbox` diperlukan untuk Docker container environment.

---

## PlaywrightBrowserPool
**File**: `configuration/PlaywrightBrowserPool.java`

**Fungsi**: Connection pool untuk browser contexts — mencegah bottleneck saat request PDF bersamaan.

```java
@Component
public class PlaywrightBrowserPool {

    private final BlockingQueue<BrowserContext> pool;

    // Ambil context dari pool (blocking jika pool kosong)
    public BrowserContext acquire() throws InterruptedException;

    // Kembalikan context ke pool setelah selesai
    public void release(BrowserContext context);

    // Pool size default: 3 concurrent contexts
}
```

**Cara pakai di GeneratePdfFiles**:
```java
BrowserContext context = browserPool.acquire();
try {
    Page page = context.newPage();
    page.navigate(url);
    byte[] pdf = page.pdf(new Page.PdfOptions().setFormat("A4"));
    return pdf;
} finally {
    browserPool.release(context);
}
```

---

## S3ClientConfiguration
**File**: `configuration/S3ClientConfiguration.java`

**Fungsi**: Setup AWS SDK v2 S3 client untuk Cloudflare R2 (S3-compatible object storage).

```java
@Configuration
public class S3ClientConfiguration {

    @Bean
    public S3Client s3Client(
        @Value("${r2.endpoint}") String endpoint,
        @Value("${r2.access-key}") String accessKey,
        @Value("${r2.secret-key}") String secretKey,
        @Value("${r2.region}") String region
    ) {
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                )
            )
            .region(Region.of(region))
            .build();
    }
}
```

**Properties yang dibutuhkan**:
```properties
r2.endpoint=https://<account-id>.r2.cloudflarestorage.com
r2.access-key=your_r2_access_key
r2.secret-key=your_r2_secret_key
r2.region=auto
r2.bucket=cek-pelunasan-files
```

---

## WebClientConfiguration
**File**: `configuration/WebClientConfiguration.java`

**Fungsi**: Configure `WebClient` bean untuk HTTP calls (WhatsApp gateway, SLIK endpoint).

```java
@Configuration
public class WebClientConfiguration {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .codecs(config ->
                config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) // 10MB
            );
    }

    @Bean
    @Qualifier("whatsappClient")
    public WebClient whatsappWebClient(
        WebClient.Builder builder,
        @Value("${whatsapp.gateway.url}") String baseUrl,
        @Value("${whatsapp.gateway.token}") String token
    ) {
        return builder
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + token)
            .build();
    }

    @Bean
    @Qualifier("slikClient")
    public WebClient slikWebClient(
        WebClient.Builder builder,
        @Value("${slik.endpoint.url}") String baseUrl
    ) {
        return builder.baseUrl(baseUrl).build();
    }
}
```

---

## AsyncConfiguration
**File**: `configuration/AsyncConfiguration.java`

**Fungsi**: Configure thread pool untuk `@Async` tasks.

```java
@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("bot-async-");
        executor.initialize();
        return executor;
    }
}
```

**Digunakan oleh**:
- `Routers.java` (WhatsApp message processing)
- `SendNotificationSlikUpdated.java`
- `BroadcastCommandHandler.java`

---

## application.properties — Semua Properties

```properties
# Server
server.port=8080

# MongoDB
spring.data.mongodb.uri=mongodb://user:pass@host:27017/cek-pelunasan

# Telegram TDLight
telegram.api.id=12345678
telegram.api.hash=abcdef1234567890
telegram.bot.token=123456:ABC-DEF...
telegram.session.path=/app/session
telegram.owner.chat-id=123456789

# WhatsApp Gateway
whatsapp.gateway.url=http://localhost:3000
whatsapp.gateway.token=your_gateway_token
whatsapp.gateway.sender=628xxx@s.whatsapp.net

# Cloudflare R2 (S3-compatible)
r2.endpoint=https://<account-id>.r2.cloudflarestorage.com
r2.access-key=...
r2.secret-key=...
r2.region=auto
r2.bucket=cek-pelunasan-files

# SLIK PDF Service
slik.endpoint.url=https://slik.example.com
slik.endpoint.username=user
slik.endpoint.password=pass

# Mail (SMTP)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=email@gmail.com
spring.mail.password=app_password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# Actuator
management.endpoints.web.exposure.include=health,metrics,prometheus
management.endpoint.health.show-details=always

# Logging
logging.level.org.cekpelunasan=INFO
```

---

## Build Profiles (pom.xml)

### Linux Profile (Production)
```xml
<profile>
    <id>linux</id>
    <dependencies>
        <dependency>
            <groupId>it.tdlight</groupId>
            <artifactId>tdlight-natives-linux-amd64-openssl3</artifactId>
        </dependency>
    </dependencies>
</profile>
```

**Aktifkan**: `mvn package -Plinux`

### Windows Profile (Development)
```xml
<profile>
    <id>windows</id>
    <dependencies>
        <dependency>
            <groupId>it.tdlight</groupId>
            <artifactId>tdlight-natives-windows-amd64</artifactId>
        </dependency>
    </dependencies>
</profile>
```

**Aktifkan**: `mvn package -Pwindows`

---

## Docker Setup

### Dockerfile
```dockerfile
FROM eclipse-temurin:21-jre
# Copy jar
COPY target/cek-pelunasan-*.jar app.jar
# Playwright dependencies (Chromium)
RUN apt-get install -y chromium
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml
```yaml
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/cek-pelunasan
    depends_on:
      - mongo
  mongo:
    image: mongo:7
    volumes:
      - mongo-data:/data/db
```
