# ===== Build stage =====
FROM gradle:8.8-jdk21 AS builder
WORKDIR /app

COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies || true

COPY . .
RUN chmod +x gradlew
RUN ./gradlew --no-daemon clean bootJar

# ===== Runtime stage =====
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8081

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar --server.port=${SERVER_PORT:-8081} --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-local}"]
