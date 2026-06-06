# ---- Build ----
FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew

# Download dependencies first (layer cache)
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon -q 2>/dev/null || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime ----
FROM eclipse-temurin:25-jre-noble AS runner
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r spring && useradd -r -g spring -u 1001 spring

COPY --from=builder --chown=spring:spring /app/build/libs/*.jar app.jar

USER spring
EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Duser.timezone=Asia/Tokyo", \
  "-jar", "/app/app.jar"]
