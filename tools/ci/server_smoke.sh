#!/usr/bin/env bash
# П-13 / открытая проблема 3: сервер на синтетическом уровне (без данных GSC) создаёт новый мир, держит тик и сохраняется.
# Запуск: bash tools/ci/server_smoke.sh   (из корня репозитория; нужен JDK 17)
# Итог — последняя строка: «СЕРВЕР: OK …» или «СЕРВЕР: ПРОВАЛ …»; код выхода 0/1. Лог сервера — /tmp/sz-server.log.
# Порог TPS — SMOKE_MIN_TPS (по умолчанию 15), время на подъём мира — SMOKE_START_TIMEOUT (900 с).
set -u
cd "$(git rev-parse --show-toplevel)"
LOG=/tmp/sz-server.log; : > "$LOG"
MIN_TPS=${SMOKE_MIN_TPS:-15}; START_TIMEOUT=${SMOKE_START_TIMEOUT:-900}
WORLD="smoke-$(date +%s)"
fail() { echo "СЕРВЕР: ПРОВАЛ — $1"; grep -m20 -E "Exception|ERROR|Crash|FATAL|СТОРОЖ" "$LOG" | cut -c1-300; stop_server; exit 1; }
stop_server() {
  [ -n "${PID:-}" ] || return 0
  python3 -I tools/ci/rcon.py stop >/dev/null 2>&1 || true
  for _ in $(seq 1 60); do kill -0 "$PID" 2>/dev/null || return 0; sleep 2; done
  pkill -P "$PID" 2>/dev/null; kill "$PID" 2>/dev/null; true
}

mkdir -p run/stalkerzone/levels
python3 -I tools/xray/fake_szl.py run/stalkerzone/levels/l01_escape.szl >/dev/null || { echo "СЕРВЕР: ПРОВАЛ — нет синтетического уровня"; exit 1; }
echo "eula=true" > run/eula.txt
cat > run/server.properties <<EOF
level-type=stalkerzone\:zone
level-name=$WORLD
enable-rcon=true
rcon.password=${RCON_PASSWORD:-sz}
rcon.port=${RCON_PORT:-25575}
max-tick-time=-1
online-mode=false
view-distance=10
simulation-distance=8
spawn-protection=0
EOF

( timeout $((START_TIMEOUT + 600)) ./gradlew runServer --no-daemon --console=plain > "$LOG" 2>&1 ) &
PID=$!
t0=$(date +%s)
until grep -qE "Done \(|Crash|Failed to load registries|BUILD FAILED" "$LOG"; do
  kill -0 "$PID" 2>/dev/null || fail "сервер завершился до «Done»"
  [ $(( $(date +%s) - t0 )) -gt "$START_TIMEOUT" ] && fail "мир не создан за $START_TIMEOUT с"
  sleep 5
done
grep -qE "Crash|Failed to load registries|BUILD FAILED" "$LOG" && fail "ошибка при запуске"
grep -q "Unbound values" "$LOG" && fail "ошибки реестра мира (Unbound values)"
START=$(( $(date +%s) - t0 ))

# чанк деревни (там уровень и площадки), потом мир живёт минуту: тик должен идти, сторож молчать
python3 -I tools/ci/rcon.py "forceload add 0 0" >/dev/null 2>&1 || fail "RCON не отвечает"
sleep 60
TPS_LINE=$(python3 -I tools/ci/rcon.py "forge tps" 2>/dev/null | tr '\n' ' ')
TPS=$(printf '%s' "$TPS_LINE" | grep -oE "Overall: Mean tick time: [0-9.]+ ms. Mean TPS: [0-9.]+" | grep -oE "[0-9.]+$")
[ -z "$TPS" ] && TPS=$(printf '%s' "$TPS_LINE" | grep -oE "Mean TPS: [0-9.]+" | tail -1 | grep -oE "[0-9.]+$")
[ -z "$TPS" ] && fail "нет ответа RCON на «forge tps»: ${TPS_LINE:-пусто}"
grep -q "СТОРОЖ" "$LOG" && fail "сторож: сервер не тикает"
python3 -c "import sys; sys.exit(0 if float('$TPS') >= float('$MIN_TPS') else 1)" || fail "TPS $TPS < $MIN_TPS"

python3 -I tools/ci/rcon.py "save-all flush" >/dev/null 2>&1 || fail "RCON: save-all"
python3 -I tools/ci/rcon.py stop >/dev/null 2>&1 || true
for _ in $(seq 1 90); do kill -0 "$PID" 2>/dev/null || break; sleep 2; done
kill -0 "$PID" 2>/dev/null && fail "сервер не остановился за 3 мин после stop"
grep -qE "Saving worlds|All chunks are saved|All dimensions are saved" "$LOG" || fail "нет «Saving worlds» при остановке"
PID=""
# строка LevelRegions: «уровень l01_escape загружен: … x0/z0/w/d …» или «… не найден: нет файла …» (кириллица в логе может быть «?»)
grep -q "LevelRegions.*l01_escape.*x0/z0/w/d" "$LOG" || { echo "СЕРВЕР: ПРОВАЛ — мод не загрузил синтетический уровень l01_escape"; grep -m2 "LevelRegions" "$LOG" | cut -c1-300; exit 1; }
echo "СЕРВЕР: OK — мир «$WORLD» создан за $START с, TPS $TPS, сохранение при stop"
