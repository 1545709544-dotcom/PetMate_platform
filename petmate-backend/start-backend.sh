#!/usr/bin/env bash
# PetMate 后端启动脚本（带 GLM API Key）
# 用法: bash start-backend.sh
set -a
[ -f "$(dirname "$0")/.env" ] && source "$(dirname "$0")/.env"
set +a

export SERVER_PORT=8080
cd "$(dirname "$0")"

# 停掉占用 8080 的旧进程
PID=$(netstat -ano 2>/dev/null | grep ':8080' | grep LISTENING | head -1 | awk '{print $5}')
if [ -n "$PID" ]; then
  echo "killing old backend pid $PID"
  taskkill //PID "$PID" //F >/dev/null 2>&1
  sleep 1
fi

echo "starting petmate-backend (ZHIPU_API_KEY loaded: ${ZHIPU_API_KEY:+yes})"
nohup java -jar target/petmate-backend.jar > /tmp/petmate-backend.log 2>&1 &
echo "backend pid $!"

for i in $(seq 1 30); do
  if netstat -ano 2>/dev/null | grep -q ':8080.*LISTENING'; then
    echo "backend up after ${i}s"; break
  fi
  sleep 1
done
grep "ai-boot" /tmp/petmate-backend.log | tail -1
