#!/bin/sh
set -e

BACKEND_PORT="${BACKEND_PORT:-8080}"
NGINX_PORT="${NGINX_PORT:-80}"

echo "[entrypoint] starting backend on port ${BACKEND_PORT} ..."
java ${JAVA_OPTS} -Dserver.port=${BACKEND_PORT} -jar /app/app.jar &
BACKEND_PID=$!

trap 'echo "[entrypoint] stopping backend"; kill -TERM $BACKEND_PID 2>/dev/null; nginx -s quit 2>/dev/null || true; wait $BACKEND_PID 2>/dev/null || true; exit 0' INT TERM EXIT

echo "[entrypoint] starting nginx on port ${NGINX_PORT} ..."
nginx -g 'daemon off;'