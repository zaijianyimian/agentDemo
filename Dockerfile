# syntax=docker/dockerfile:1.6

# -----------------------------------------------------------------------------
# 阶段 1: 构建后端 bootJar (Spring Boot)
# -----------------------------------------------------------------------------
FROM gradle:8.14.4-jdk17 AS backend-builder

WORKDIR /workspace

COPY agentDemo/gradlew agentDemo/settings.gradle agentDemo/build.gradle ./
COPY agentDemo/gradle ./gradle
COPY agentDemo/src ./src
COPY agentDemo/src/main/resources/application.example.yaml ./src/main/resources/application.yaml

RUN chmod +x ./gradlew \
 && ./gradlew --no-daemon clean bootJar -x test \
 && cp build/libs/*.jar /workspace/app.jar

# -----------------------------------------------------------------------------
# 阶段 2: 构建前端 dist (Vue 3 + Vite)
# -----------------------------------------------------------------------------
FROM node:20-alpine AS frontend-builder

WORKDIR /workspace

COPY agentDemo/frontend/package.json agentDemo/frontend/package-lock.json* ./
RUN npm install --no-audit --no-fund

COPY agentDemo/frontend ./
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
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:HeapDumpPath=/app/logs -Dfile.encoding=UTF-8" \
    SERVER_PORT=8000

EXPOSE 8000

# 持久化目录
VOLUME ["/app/data", "/app/generated", "/app/logs"]

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=90s \
    CMD wget -qO- http://localhost:8000/actuator/health/liveness || exit 1

ENTRYPOINT ["/sbin/tini", "--", "java", "-jar", "/app/app.jar"]

# -----------------------------------------------------------------------------
# 阶段 3b: 前端镜像 - nginx :3000 静态 SPA
# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS frontend

RUN apk add --no-cache wget

COPY --from=frontend-builder /workspace/dist /usr/share/nginx/html
COPY agentDemo/docker/nginx-frontend.conf /etc/nginx/conf.d/default.conf
COPY agentDemo/docker/nginx-proxy-common.conf /etc/nginx/snippets/proxy_common.conf

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:3000/nginx-health || exit 1

# -----------------------------------------------------------------------------
# 阶段 3b2: Python Graph 服务镜像
# -----------------------------------------------------------------------------
# 日程写入依赖 Java 内部接口，因此 Python 镜像必须与 Java 共享 INTERNAL_TOKEN。
# 上游地址通过环境变量注入，容器内不能写 127.0.0.1（那是容器自身）。
FROM python:3.14-slim AS graph

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1

RUN apt-get update \
 && apt-get install -y --no-install-recommends libpq5 curl \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /srv/graph

# 先装依赖，利用镜像层缓存：源码变更不会触发依赖重装。
COPY graph/pyproject.toml graph/uv.lock ./
RUN pip install --upgrade pip uv \
 && uv sync --locked --no-install-project --no-dev

COPY graph/app ./app
COPY graph/migrations ./migrations
COPY graph/test ./test

# 同步项目本身（pyproject 无构建后端，仅安装依赖即可）。
RUN uv sync --locked --no-dev

ENV PATH="/srv/graph/.venv/bin:$PATH" \
    PYTHONPATH=/srv/graph

EXPOSE 8001

HEALTHCHECK --interval=30s --timeout=5s --retries=5 --start-period=40s \
    CMD curl -fsS http://localhost:8001/health || exit 1

CMD ["python", "-m", "app.serve"]

# -----------------------------------------------------------------------------
# 阶段 3c: 默认完整镜像 - Spring Boot :8000 + nginx :3000
# docker build 未指定 --target 时构建此阶段，兼容单容器部署。
#
# 该阶段不包含 Python：Python 需要 PostgreSQL 与 Java 内部接口，
# 请使用 docker compose 拉起完整拓扑（backend / graph / frontend / mysql /
# postgres / rabbitmq），或分服务部署。AI 前缀在缺少 graph 上游时返回 502，
# 属于部署不完整，不会静默回落到 Java。
# -----------------------------------------------------------------------------
FROM backend AS runtime

RUN apk add --no-cache nginx supervisor \
 && mkdir -p /run/nginx /usr/share/nginx/html /etc/nginx/snippets /etc/supervisor.d

COPY --from=frontend-builder /workspace/dist /usr/share/nginx/html
COPY agentDemo/docker/nginx-frontend.conf /etc/nginx/http.d/default.conf
COPY agentDemo/docker/nginx-proxy-common.conf /etc/nginx/snippets/proxy_common.conf
COPY agentDemo/docker/supervisord.ini /etc/supervisor.d/agentdemo.ini
RUN sed -i 's#http://backend:8000#http://127.0.0.1:8000#g' /etc/nginx/http.d/default.conf

EXPOSE 3000 8000

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=90s \
    CMD wget -qO- http://localhost:3000/nginx-health >/dev/null \
     && wget -qO- http://localhost:8000/actuator/health/liveness >/dev/null \
     || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["/usr/bin/supervisord", "-c", "/etc/supervisord.conf"]
