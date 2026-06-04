# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:23-jdk-alpine AS builder
WORKDIR /build

COPY . .
RUN ./gradlew buildFatJar --no-daemon

FROM eclipse-temurin:23-jre-alpine
WORKDIR /app

# wget for healthcheck
RUN apk add --no-cache wget

COPY --from=builder /build/build/libs/*-all.jar app.jar

EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget --quiet --tries=1 -O /dev/null http://localhost:8081/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
