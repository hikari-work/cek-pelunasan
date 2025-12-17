FROM maven:3.9.6-amazoncorretto-21 AS build
WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests


FROM amazoncorretto:21-al2023-jdk
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-XX:+UseShenandoahGC", "-Xmx512m", "-Xmn256m", "-jar", "app.jar", "--server.port=8080"]
