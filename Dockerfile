# syntax=docker/dockerfile:1

# Spring Boot JAR는 CPU 아키텍처에 종속되지 않으므로 빌드는 CI 러너에서 수행한다.
FROM --platform=$BUILDPLATFORM gradle:8.8-jdk21 AS builder
WORKDIR /app

COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies || true

COPY . .
RUN chmod +x gradlew
RUN ./gradlew --no-daemon clean bootJar

# 실행 이미지는 Buildx가 요청한 대상 아키텍처(개발 arm64, 운영 amd64)를 따른다.
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8081

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar --server.port=${SERVER_PORT:-8081} --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-local}"]
