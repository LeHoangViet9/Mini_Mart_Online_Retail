FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services services
RUN mvn -B -pl services/shipping-service -am package -DskipTests
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S minimart && adduser -S minimart -G minimart
COPY --from=build /workspace/services/shipping-service/target/shipping-service-0.1.0.jar app.jar
USER minimart
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
