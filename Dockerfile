# syntax=docker/dockerfile:1.6

# -----------------------------------------------------------------------------
# 阶段 1: 构建后端 bootJar (Spring Boot)
# -----------------------------------------------------------------------------
FROM gradle:8.14.4-jdk17 AS backend-builder

WORKDIR /workspace

# 先复制 wrapper 与依赖文件，最大化缓存命中
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
# 阶段 3: 运行时镜像 - nginx 静态服务 + 反代 /api 到 Spring Boot
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# nginx + wget（healthcheck 用） + tini（信号转发） + tzdata
RUN apk add --no-cache nginx wget tini tzdata curl \
 && mkdir -p /app/data /app/generated /app/logs /var/cache/nginx /var/log/nginx /run/nginx

# 前端 dist
COPY --from=frontend-builder /workspace/dist /usr/share/nginx/html

# nginx 配置（前端静态 + /api 反代到本机 8080 后端）
COPY docker/nginx.conf /etc/nginx/nginx.conf

# 后端 jar 与启动脚本
COPY --from=backend-builder /workspace/app.jar /app/app.jar
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# 容器默认时区
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:HeapDumpPath=/app/logs -Dfile.encoding=UTF-8" \
    BACKEND_PORT=8080 \
    NGINX_PORT=80

EXPOSE 80

VOLUME ["/app/data", "/app/generated", "/app/logs"]

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=90s \
    CMD wget -qO- http://localhost:80/actuator/health/liveness || exit 1

ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/entrypoint.sh"]