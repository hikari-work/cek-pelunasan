FROM amazoncorretto:21-al2023-jdk
WORKDIR /app
COPY app.jar app.jar

# Cloud Run menggunakan variabel PORT, kita arahkan server.port ke sana
ENTRYPOINT ["java", "-XX:+UseShenandoahGC", "-Xmx756m", "-Xmn128m", "-jar", "app.jar", "--server.port=5000"]
