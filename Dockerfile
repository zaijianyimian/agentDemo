# syntax=docker/dockerfile:1.6

# -----------------------------------------------------------------------------
# 阶段 1: 构建后端 bootJar (Spring Boot)
# -----------------------------------------------------------------------------
FROM gradle:8.14.4-jdk17 AS backend-builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew \
 && ./gradlew --no-daemon clean bootJar -x test \
 && cp build/libs/*.jar /workspace/app.jar

# -----------------------------------------------------------------------------
# 阶段 2: 构建前端 dist (Vue 3 + Vite)
# -----------------------------------------------------------------------------
FROM node:20-alpine AS frontend-builder

WORKDIR /workspace

COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install --no-audit --no-fund

COPY frontend ./
RUN npm run build \
 && rm -rf node_modules

# -----------------------------------------------------------------------------
# 阶段 3a: 后端镜像 - 仅 Spring Boot :8000
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS backend

RUN apk add --no-cache wget tini tzdata \
 && mkdir -p /app/data /app/generated /app/logs

COPY --from=backend-builder /workspace/app.jar /app/app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:HeapDumpPath=/app/logs -Dfile.encoding=UTF-8" \
    SERVER_PORT=8000

EXPOSE 8000

# 持久化目录
VOLUME ["/app/data", "/app/generated", "/app/logs"]

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=90s \
    CMD wget -qO- http://localhost:8000/actuator/health/liveness || exit 1

ENTRYPOINT ["/sbin/tini", "--", "java", "$JAVA_OPTS", "-Dserver.port=${SERVER_PORT}", "-jar", "/app/app.jar"]

# -----------------------------------------------------------------------------
# 阶段 3b: 前端镜像 - nginx :3000 静态 SPA
# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS frontend

RUN apk add --no-cache wget

COPY --from=frontend-builder /workspace/dist /usr/share/nginx/html
COPY docker/nginx-frontend.conf /etc/nginx/conf.d/default.conf

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:3000/nginx-health || exit 1