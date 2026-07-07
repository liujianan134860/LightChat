FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :server:clean :server:jar --no-daemon

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/server/build/libs/server-1.0.0.jar /app/lightchat-server.jar
RUN jar tf /app/lightchat-server.jar | grep -q 'com/lightchat/server/MainKt.class'

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
EXPOSE 8080 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8081/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/lightchat-server.jar"]
