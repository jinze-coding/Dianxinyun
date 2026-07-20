#!/usr/bin/env bash

set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
WEB_DIR="$ROOT_DIR/frontend"
MINIPROGRAM_DIR="$ROOT_DIR/wechat-miniprogram/site-platform-miniprogram"
LOG_DIR="$ROOT_DIR/logs/dev-services"

BACKEND_SESSION="dianxinyun-backend"
WEB_SESSION="dianxinyun-web"
MINIPROGRAM_SESSION="dianxinyun-miniprogram"
MINIPROGRAM_H5_SESSION="dianxinyun-miniprogram-h5"

BACKEND_URL="http://127.0.0.1:8080/doc.html"
WEB_URL="http://127.0.0.1:3002"
MINIPROGRAM_H5_URL="http://127.0.0.1:3003"
MINIPROGRAM_APP_JSON="$MINIPROGRAM_DIR/dist/dev/mp-weixin/app.json"

mkdir -p "$LOG_DIR"

info() {
  printf '[INFO] %s\n' "$*"
}

warn() {
  printf '[WARN] %s\n' "$*" >&2
}

fail() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令：$1"
}

screen_exists() {
  screen -ls 2>/dev/null | grep -q "[.]$1[[:space:]]"
}

listener_pid() {
  lsof -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null || true
}

wait_for_http() {
  local name="$1"
  local url="$2"
  local attempts="${3:-60}"
  local current=0

  while [ "$current" -lt "$attempts" ]; do
    if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then
      info "$name 已就绪：$url"
      return 0
    fi
    current=$((current + 1))
    sleep 1
  done

  warn "$name 未在预期时间内就绪，请查看 $LOG_DIR"
  return 1
}

wait_for_file() {
  local name="$1"
  local path="$2"
  local attempts="${3:-60}"
  local current=0

  while [ "$current" -lt "$attempts" ]; do
    if [ -s "$path" ]; then
      info "$name 编译产物已生成：$path"
      return 0
    fi
    current=$((current + 1))
    sleep 1
  done

  warn "$name 未生成预期产物，请查看 $LOG_DIR/miniprogram.log"
  return 1
}

check_dependencies() {
  require_command screen
  require_command curl
  require_command lsof
  require_command mvn
  require_command npm

  if ! mysqladmin ping -h 127.0.0.1 -u root --silent >/dev/null 2>&1; then
    fail "MySQL 未运行，请先启动本机 MySQL"
  fi

  if ! redis-cli ping >/dev/null 2>&1; then
    fail "Redis 未运行，请先启动本机 Redis"
  fi
}

start_backend() {
  if curl -fsS --max-time 2 "$BACKEND_URL" >/dev/null 2>&1; then
    info "共享后端已在 8080 端口运行"
    return 0
  fi

  if [ -n "$(listener_pid 8080)" ]; then
    fail "8080 端口已被其它进程占用"
  fi

  : > "$LOG_DIR/backend.log"
  screen -dmS "$BACKEND_SESSION" bash -lc \
    "cd '$BACKEND_DIR' && exec mvn spring-boot:run >> '$LOG_DIR/backend.log' 2>&1"
  wait_for_http "共享后端" "$BACKEND_URL" 90
}

start_web() {
  if curl -fsS --max-time 2 "$WEB_URL" >/dev/null 2>&1; then
    info "PC Web 已在 3002 端口运行"
    return 0
  fi

  if [ -n "$(listener_pid 3002)" ]; then
    fail "3002 端口已被其它进程占用"
  fi

  : > "$LOG_DIR/web.log"
  screen -dmS "$WEB_SESSION" bash -lc \
    "cd '$WEB_DIR' && exec npm run dev -- --host 0.0.0.0 >> '$LOG_DIR/web.log' 2>&1"
  wait_for_http "PC Web" "$WEB_URL" 60
}

start_miniprogram() {
  if screen_exists "$MINIPROGRAM_SESSION"; then
    info "小程序微信编译监听已运行"
    return 0
  fi

  : > "$LOG_DIR/miniprogram.log"
  rm -f "$MINIPROGRAM_APP_JSON"
  screen -dmS "$MINIPROGRAM_SESSION" bash -lc \
    "cd '$MINIPROGRAM_DIR' && exec env VITE_USE_MOCK=false npm run dev:mp-weixin:real >> '$LOG_DIR/miniprogram.log' 2>&1"
  wait_for_file "小程序" "$MINIPROGRAM_APP_JSON" 90
}

start_miniprogram_h5() {
  if curl -fsS --max-time 2 "$MINIPROGRAM_H5_URL" >/dev/null 2>&1; then
    info "小程序 H5 扫码预览已在 3003 端口运行"
    return 0
  fi

  if [ -n "$(listener_pid 3003)" ]; then
    fail "3003 端口已被其它进程占用"
  fi

  : > "$LOG_DIR/miniprogram-h5.log"
  screen -dmS "$MINIPROGRAM_H5_SESSION" bash -lc \
    "cd '$MINIPROGRAM_DIR' && exec env VITE_USE_MOCK=false npm run dev:h5:real >> '$LOG_DIR/miniprogram-h5.log' 2>&1"
  wait_for_http "小程序 H5 扫码预览" "$MINIPROGRAM_H5_URL" 60
}

stop_screen() {
  local session="$1"
  local label="$2"

  if screen_exists "$session"; then
    screen -S "$session" -X quit >/dev/null 2>&1 || true
    info "$label 已停止"
  fi
}

stop_port() {
  local port="$1"
  local label="$2"
  local pids
  local attempt=0

  pids="$(listener_pid "$port")"
  [ -z "$pids" ] && return 0

  kill $pids >/dev/null 2>&1 || true
  while [ "$attempt" -lt 20 ] && [ -n "$(listener_pid "$port")" ]; do
    attempt=$((attempt + 1))
    sleep 0.25
  done

  pids="$(listener_pid "$port")"
  if [ -n "$pids" ]; then
    kill -9 $pids >/dev/null 2>&1 || true
  fi
  info "$label 端口 $port 已释放"
}

stop_miniprogram_watchers() {
  local pattern="$MINIPROGRAM_DIR/node_modules/.bin/uni -p mp-weixin"
  local pids
  local attempt=0

  pids="$(pgrep -f "$pattern" 2>/dev/null || true)"
  [ -z "$pids" ] && return 0

  kill $pids >/dev/null 2>&1 || true
  while [ "$attempt" -lt 20 ] && [ -n "$(pgrep -f "$pattern" 2>/dev/null || true)" ]; do
    attempt=$((attempt + 1))
    sleep 0.25
  done

  pids="$(pgrep -f "$pattern" 2>/dev/null || true)"
  if [ -n "$pids" ]; then
    kill -9 $pids >/dev/null 2>&1 || true
  fi
  info "已清理本项目遗留的小程序编译监听"
}

stop_all() {
  stop_screen "$MINIPROGRAM_H5_SESSION" "小程序 H5 扫码预览"
  stop_screen "$MINIPROGRAM_SESSION" "小程序微信编译监听"
  stop_screen "$WEB_SESSION" "PC Web"
  stop_screen "$BACKEND_SESSION" "共享后端"

  # 兼容清理以前从普通终端或临时执行会话启动的本项目服务。
  stop_port 3002 "PC Web"
  stop_port 3003 "小程序 H5 扫码预览"
  stop_port 8080 "共享后端"
  stop_miniprogram_watchers
}

show_status() {
  local backend_state="未运行"
  local web_state="未运行"
  local miniprogram_state="未运行"
  local miniprogram_h5_state="未运行"

  curl -fsS --max-time 2 "$BACKEND_URL" >/dev/null 2>&1 && backend_state="运行中"
  curl -fsS --max-time 2 "$WEB_URL" >/dev/null 2>&1 && web_state="运行中"
  screen_exists "$MINIPROGRAM_SESSION" && miniprogram_state="编译监听中"
  curl -fsS --max-time 2 "$MINIPROGRAM_H5_URL" >/dev/null 2>&1 && miniprogram_h5_state="运行中"

  printf '共享后端：%s  %s\n' "$backend_state" "$BACKEND_URL"
  printf 'PC Web： %s  %s\n' "$web_state" "$WEB_URL"
  printf '小程序： %s  %s\n' "$miniprogram_state" "$MINIPROGRAM_APP_JSON"
  printf '扫码H5：%s  %s\n' "$miniprogram_h5_state" "$MINIPROGRAM_H5_URL"
  printf '运行日志：%s\n' "$LOG_DIR"
}

open_miniprogram() {
  local cli="/Applications/wechatwebdevtools.app/Contents/MacOS/cli"
  local login_output
  local login_image
  local login_pid
  local login_ready=0
  local attempt=0

  [ -s "$MINIPROGRAM_APP_JSON" ] || fail "小程序编译产物不存在，请先执行 restart 或 start"

  if [ -x "$cli" ]; then
    if ! "$cli" islogin --lang zh 2>/dev/null | grep -q '"login":true'; then
      login_output="$(mktemp /tmp/dianxinyun-wechat-login.XXXXXX)"
      login_image="${login_output}.jpg"

      "$cli" login --lang zh --qr-format base64 > "$login_output" 2>&1 &
      login_pid=$!

      while [ "$attempt" -lt 30 ]; do
        if grep -q 'data:image/jpeg;base64,' "$login_output"; then
          perl -ne 'if (/(data:image\/jpeg;base64,[A-Za-z0-9+\/=]+)/) { print $1 }' "$login_output" \
            | sed 's#^data:image/jpeg;base64,##' \
            | base64 -D > "$login_image"
          open "$login_image"
          login_ready=1
          break
        fi

        if ! kill -0 "$login_pid" >/dev/null 2>&1; then
          break
        fi
        attempt=$((attempt + 1))
        sleep 1
      done

      if [ "$login_ready" -ne 1 ]; then
        kill "$login_pid" >/dev/null 2>&1 || true
        rm -f "$login_output" "$login_image"
        fail "无法生成微信开发者工具登录二维码"
      fi

      info "微信开发者工具登录已过期，请扫描已打开的二维码并在手机上确认"
      wait "$login_pid" || true

      if ! "$cli" islogin --lang zh 2>/dev/null | grep -q '"login":true'; then
        rm -f "$login_output" "$login_image"
        fail "微信开发者工具登录未完成，请重新执行 open-miniprogram"
      fi

      rm -f "$login_output" "$login_image"
    fi

    # 每次打开都清理编译缓存，避免开发者工具继续运行旧的 mock 构建。
    "$cli" cache --clean compile --project "$MINIPROGRAM_DIR/dist/dev/mp-weixin" --lang zh >/dev/null 2>&1 || true
    "$cli" open --project "$MINIPROGRAM_DIR/dist/dev/mp-weixin"
  else
    open -a "微信开发者工具" "$MINIPROGRAM_DIR/dist/dev/mp-weixin"
  fi
}

start_all() {
  check_dependencies
  start_backend
  start_web
  start_miniprogram_h5
  start_miniprogram
  show_status
}

usage() {
  cat <<'EOF'
用法：scripts/dev-services.sh <start|stop|restart|status|open-miniprogram>

  start             启动共享后端、PC Web、扫码 H5 和小程序微信编译监听
  stop              停止本项目开发服务
  restart           重启全部开发服务
  status            查看服务状态和地址
  open-miniprogram  使用微信开发者工具打开小程序开发产物
EOF
}

case "${1:-status}" in
  start)
    start_all
    ;;
  stop)
    stop_all
    show_status
    ;;
  restart)
    stop_all
    start_all
    ;;
  status)
    show_status
    ;;
  open-miniprogram)
    open_miniprogram
    ;;
  *)
    usage
    exit 1
    ;;
esac
