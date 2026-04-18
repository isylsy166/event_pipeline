FROM gradle:8.7-jdk21 AS builder
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/install/event_pipeline ./
CMD ["./bin/event_pipeline"]