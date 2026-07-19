FROM gradle:8.7-jdk17 AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle settings.gradle
COPY build.gradle build.gradle
COPY src src

RUN chmod +x ./gradlew && ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

ENV TZ=Asia/Shanghai \
    # 用 Alpine 镜像减半体积（~200MB → ~90MB）。
    # MaxRAMPercentage 跟随容器内存限制自动伸缩堆。
    # ExitOnOutOfMemoryError + HeapDumpPath 保证 OOM 时有现场可查。
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:HeapDumpPath=/app/logs -Dfile.encoding=UTF-8"

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

RUN apk add --no-cache wget && \
    mkdir -p /app/data /app/generated /app/logs

EXPOSE 8000

VOLUME ["/app/data", "/app/generated", "/app/logs"]

# wget 用于 docker-compose healthcheck 探活
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD wget -qO- http://localhost:8000/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
