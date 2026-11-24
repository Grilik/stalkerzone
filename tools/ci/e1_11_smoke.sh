#!/usr/bin/env bash
# Э1-11: smoke золотой копии и simSeed на реальном мире TheZone (выделенный сервер, dev run/).
# Проверяет: создание мира из золотой копии ядром (манифест THEZONE, без чужой истории симуляции),
# загрузку мира сервером (режим THEZONE), случайный simSeed, сохранение и НЕИЗМЕННОСТЬ simSeed
# после перезапуска, обычное создание мира (LEGACY) без изменений. GUI-кнопка «Зона» — плейтест.
# Запуск: bash tools/ci/e1_11_smoke.sh [каталог золотых копий] [рабочая копия TheZone]
# Если целых версий нет и рабочая копия доступна — создаётся v1 (golden_copy.py, источник только читается).
# Итог — последняя строка «Э1-11 SMOKE: OK …» или «Э1-11 SMOKE: ПРОВАЛ …»; код выхода 0/1.
# Миры остаются в run/ (dev, не публикуются); имена уникальны на каждый запуск.
# Выделенный сервер хранит миры в рабочем каталоге (run/<level-name>), клиент — в <папка игры>/saves;
# кнопка «Зона» создаёт мир в saves (клиент), smoke проверяет ядро на серверном каталоге run/.
set -u
cd "$(git rev-parse --show-toplevel)"
GOLDEN=${1:-run/stalkerzone/golden}
WORK=${2:-C:/MineStalker/TheZone-work}
LOG=/tmp/sz-e1-11.log
START_TIMEOUT=${SMOKE_START_TIMEOUT:-900}
WORLD="ZoneSmoke-$(date +%s)"
NORMAL="ZoneNormal-$(date +%s)"
# UTF-8 для логов сервера (Java 17 на Windows иначе пишет в native-кодировке)
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dfile.encoding=UTF-8"
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dfile.encoding=UTF-8"
rcon() { timeout "${2:-150}" python3 -I tools/ci/rcon.py "$1" 2>/dev/null; }
fail() {
  echo "— диагностика (последние строки лога):"; tail -25 "$LOG" | cut -c1-300
  stop_server
  echo "Э1-11 SMOKE: ПРОВАЛ — $1"; exit 1
}
stop_server() {
  [ -n "${PID:-}" ] || return 0
  python3 -I tools/ci/rcon.py stop >/dev/null 2>&1 || true
  for _ in $(seq 1 90); do kill -0 "$PID" 2>/dev/null || return 0; sleep 2; done
  kill "$PID" 2>/dev/null; true
}
start_server() { # $1 — имя мира; лог обнуляется
  : > "$LOG"
  cat > run/server.properties <<EOF
level-name=$1
enable-rcon=true
rcon.password=${RCON_PASSWORD:-sz}
rcon.port=${RCON_PORT:-25575}
max-tick-time=-1
online-mode=false
view-distance=6
simulation-distance=4
spawn-protection=0
EOF
  ( timeout $((START_TIMEOUT + 600)) ./gradlew runServer --no-daemon --console=plain > "$LOG" 2>&1 ) &
  PID=$!
  local t0=$(date +%s)
  until grep -qE "Done \(|Crash|Failed to load registries|BUILD FAILED" "$LOG"; do
    kill -0 "$PID" 2>/dev/null || fail "сервер завершился до «Done» (мир $1)"
    [ $(( $(date +%s) - t0 )) -gt "$START_TIMEOUT" ] && fail "мир $1 не поднялся за $START_TIMEOUT с"
    sleep 5
  done
  grep -qE "Crash|Failed to load registries|BUILD FAILED" "$LOG" && fail "ошибка запуска (мир $1)"
}
seed_of() { grep -oE "simSeed=-?[0-9]+" "$LOG" | head -1 | cut -d= -f2; }

mkdir -p run/stalkerzone/levels
echo "eula=true" > run/eula.txt

# 0. чистые классы ядра (javac-набор CI) + CLI золотой копии
[ -d "out/ru/stalkerzone/world/golden" ] || javac --release 17 -encoding UTF-8 -d out $(bash tools/ci/test_sources.sh) || fail "javac"
javac -encoding UTF-8 -cp out -d out tools/thezone/GoldenCopyCli.java || fail "javac GoldenCopyCli"

# 1. золотая копия: целая версия
if ! java -Dfile.encoding=UTF-8 -cp out GoldenCopyCli verify "$GOLDEN" >/dev/null 2>&1; then
  [ -d "$WORK" ] || fail "нет целой золотой копии в $GOLDEN и рабочей копии $WORK"
  python3 -I tools/thezone/golden_copy.py make "$WORK" "$GOLDEN" || fail "golden_copy.py make не смог создать версию"
fi
java -Dfile.encoding=UTF-8 -cp out GoldenCopyCli list "$GOLDEN" || fail "list золотых копий"

# 2. мир из золотой копии — ядром Э1-11 (как кнопка «Зона», без GUI); каталог миров сервера — run/
java -Dfile.encoding=UTF-8 -cp out GoldenCopyCli create "$GOLDEN" run "$WORLD" || fail "createWorld"
grep -q '"mode": "thezone"' "run/$WORLD/stalkerzone/world.json" || fail "нет манифеста THEZONE"
[ ! -f "run/$WORLD/data/stalkerzone_sim.dat" ] || fail "в новый мир попало чужое сохранение симуляции"
echo "мир создан: run/$WORLD (манифест thezone, чужой истории симуляции нет)"

# 3. первый запуск: режим THEZONE, новый случайный simSeed
start_server "$WORLD"
grep -q "THEZONE" "$LOG" || fail "режим мира не THEZONE (манифест не прочитан)"
SEED1=$(seed_of)
[ -n "$SEED1" ] || fail "нет строки simSeed в логе"
grep -q "новый мир: simSeed" "$LOG" || fail "simSeed не создан как для нового мира"
echo "запуск 1: THEZONE, simSeed=$SEED1"
rcon "save-all flush" >/dev/null || fail "save-all flush"
[ -f "run/$WORLD/data/stalkerzone_sim.dat" ] || fail "симуляция не сохранилась в данные мира"
stop_server; PID=""
grep -qE "Saving worlds|All chunks are saved" "$LOG" || fail "нет сохранения при остановке"

# 4. перезапуск: simSeed восстановлен и не изменился
start_server "$WORLD"
SEED2=$(seed_of)
[ "$SEED1" = "$SEED2" ] || fail "simSeed изменился после перезапуска: $SEED1 → $SEED2"
grep -q "мир загружен: simSeed" "$LOG" || fail "simSeed не восстановлен из данных мира"
echo "запуск 2: simSeed=$SEED2 — не изменился"
stop_server; PID=""

# 5. обычное создание мира не сломано: новый мир LEGACY, свой simSeed
start_server "$NORMAL"
grep -q "LEGACY" "$LOG" || fail "обычный мир не LEGACY"
SEED3=$(seed_of)
[ -n "$SEED3" ] && [ "$SEED3" != "$SEED1" ] || fail "обычный мир: simSeed отсутствует или совпал с миром Зоны"
echo "обычное создание: $NORMAL, LEGACY, simSeed=$SEED3 (отличен от мира Зоны)"
stop_server; PID=""

echo "Э1-11 SMOKE: OK — мир из золотой копии THEZONE, simSeed=$SEED1 стабилен при перезапуске, обычное создание LEGACY работает (миры в run/, не публикуются)"
