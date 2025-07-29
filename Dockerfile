FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "target/cek-pelunasan-1.2.2.jar", 
  "--whatsapp.gateway.username=wI98Ya6w",
  "--whatsapp.gateway.password=53YZXiFBqgR3FfeiY5r5bEp5",
  "--whatsapp.gateway.url=https://gowa-boglmlbrosi5.axpq.sumopod.my.id/send/message",
  "--r2.account.id=7dcbb52a3bac329c00c73674287227f4",
  "--r2.access.key=a1f245c36bd59f21f70b0c13ecb7e75e",
  "--r2.secret.key=723c19c9c71b0a30166b02ce3b4eb05d7791ea1cb6c5b0137a362fc643753239",
  "--r2.bucket=slikjuli",
  "--gemini.key=AIzaSyDNZobJWbWCwHB_zMWY05ZgAleITbHeu04",
  "--spring.datasource.hikari.maximum-pool-size=100",
  "--spring.datasource.hikari.minimum-idle=5"]

