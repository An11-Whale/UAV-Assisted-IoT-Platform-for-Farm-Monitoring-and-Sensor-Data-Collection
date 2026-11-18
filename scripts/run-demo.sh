#!/bin/sh
# Demo tích hợp: farm-sim (cảm biến -> node -> UAV gateway -> server :8080) + dashboard React (:5173)
# Cần: JDK 17+, Node 20+.  Chạy từ thư mục gốc của repo:  sh scripts/run-demo.sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

( cd "$ROOT/src/farm-sim" && sh build.sh )
( cd "$ROOT/src/dashboard" && [ -d node_modules ] || npm install )

# 24 chuyến bay mô phỏng ~ 1 ngày dữ liệu; --keep giữ server sống để dashboard đọc
( cd "$ROOT/src/farm-sim" && java -cp out farm.Main --missions 24 --tick-ms 5 --keep ) &
SIM_PID=$!
trap 'kill $SIM_PID 2>/dev/null' EXIT INT TERM

sleep 8   # chờ các chuyến bay mô phỏng chạy xong
cd "$ROOT/src/dashboard" && npm run dev
