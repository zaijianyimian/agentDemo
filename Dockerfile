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
# 阶段 3: 单镜像运行时 - nginx 前端静态服务 + 反代 /api 到 Spring Boot
# 一个容器 = 一个进程组（tini 包装），对外只暴露 8000
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# nginx + wget（healthcheck 用） + tini（信号转发） + tzdata
RUN apk add --no-cache nginx wget tini tzdata curl \
 && mkdir -p /app/data /app/generated /app/logs \
    /app/runtime/nginx-cache /app/runtime/nginx-logs \
 && chown -R root:root /app/runtime

# 前端 dist（容器内 nginx 静态根目录）
COPY --from=frontend-builder /workspace/dist /usr/share/nginx/html

# nginx 配置：前端 SPA + 反代 /api、/actuator、/files 到本机 8080 Spring Boot
COPY docker/nginx.conf /etc/nginx/nginx.conf

# 后端 jar 与启动脚本
COPY --from=backend-builder /workspace/app.jar /app/app.jar
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:HeapDumpPath=/app/logs -Dfile.encoding=UTF-8" \
    BACKEND_PORT=8080 \
    NGINX_PORT=8000

EXPOSE 8000

# ===========================================================================
# 持久化目录（建议挂载到宿主机）
#   /app/data         业务数据（dispatched_task、note、memory 等业务导出）
#   /app/generated    生成物（autonomy 生成、OpenClaw workspace 备份等）
#   /app/logs         JVM heap dump + Spring Boot 日志（agentdemo.log）
#   /app/runtime/nginx-logs   nginx access/error 日志
#   /app/runtime/nginx-cache  nginx 代理缓存（可清空，建议保留）
# ===========================================================================
VOLUME ["/app/data", "/app/generated", "/app/logs", "/app/runtime/nginx-logs", "/app/runtime/nginx-cache"]

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=90s \
    CMD wget -qO- http://localhost:8000/actuator/health/liveness || exit 1

ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/entrypoint.sh"]