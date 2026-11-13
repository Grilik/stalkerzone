#!/usr/bin/env bash
# П-13 / открытая проблема 3: сервер на синтетическом уровне (без данных GSC) создаёт новый мир, держит тик и сохраняется.
# Э0-02: грузит все 9 площадок Prefabs.SITES и полосу периметра (там работает WallBuilder), проверяет аудит экономики
# («zone audit») и чистоту лога: строки Exception|ERROR|СТОРОЖ вне белого списка tools/ci/smoke_allow.txt — провал.
# Запуск: bash tools/ci/server_smoke.sh   (из корня репозитория; нужен JDK 17)
# Итог — последняя строка: «СЕРВЕР: OK …» или «СЕРВЕР: ПРОВАЛ …»; код выхода 0/1. Лог сервера — /tmp/sz-server.log.
# Порог TPS — SMOKE_MIN_TPS (по умолчанию 15), время на подъём мира — SMOKE_START_TIMEOUT (900 с),
# время жизни мира после загрузки площадок — SMOKE_LIVE (90 с).
set -u
cd "$(git rev-parse --show-toplevel)"
LOG=/tmp/sz-server.log; : > "$LOG"
ALLOW=tools/ci/smoke_allow.txt
MIN_TPS=${SMOKE_MIN_TPS:-15}; START_TIMEOUT=${SMOKE_START_TIMEOUT:-900}; LIVE=${SMOKE_LIVE:-90}
WORLD="smoke-$(date +%s)"
rcon() { timeout "${2:-150}" python3 -I tools/ci/rcon.py "$1" 2>/dev/null; }
# строки лога с ошибками, кроме белого списка (регулярные выражения grep -E, по одному в строке; # и пустые — пропуск)
log_errors() { grep -nE "Exception|ERROR|СТОРОЖ" "$LOG" | grep -vE -f <(grep -vE '^[[:space:]]*(#|$)' "$ALLOW"); }
fail() {
  echo "— диагностика (строки лога с ошибками):"; grep -m40 -E "Exception|ERROR|Crash|FATAL|СТОРОЖ" "$LOG" | cut -c1-300
  stop_server
  echo "СЕРВЕР: ПРОВАЛ — $1"; exit 1
}
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

rcon "forceload add 0 0" >/dev/null || fail "RCON не отвечает"
# Э0-02: все 9 площадок Prefabs.SITES (прямоугольники CLEAR/PLATEAU сборщиков, координаты мира) и полоса периметра
# у западной границы (ZoneBounds X -83..605: забор x=-83, барьер x=-85..-84 — единственное место, где WallBuilder
# встречает чужие блоки). Площадки за пределами view-distance 10 у игрока с обзором 20 грузятся сразу.
# Зависание 07.11: рельсы туннеля ставились с onPlace при загрузке чанка → сервер ждал соседний чанк вечно.
SITES=(
  "village|-39 -24 56 34"
  "bunker|-76 -20 -40 6"
  "south_post|28 195 107 262"
  "wagon|104 -90 134 -58"
  "dump|-74 -140 -3 -90"
  "tunnel|106 -296 158 -228"
  "atp|289 -158 355 -88"
  "elevator|176 -247 237 -191"
  "lair|31 -189 59 -161"
  "perimeter|-92 -20 -72 20"
)
for S in "${SITES[@]}"; do
  NAME=${S%%|*}; AREA=${S#*|}
  rcon "forceload add $AREA" >/dev/null || fail "RCON не отвечает после загрузки площадки $NAME ($AREA)"
  echo "площадка $NAME: forceload $AREA"
done
sleep "$LIVE"
TPS_LINE=$(rcon "forge tps" | tr '\n' ' ')
TPS=$(printf '%s' "$TPS_LINE" | grep -oE "Overall: Mean tick time: [0-9.]+ ms. Mean TPS: [0-9.]+" | grep -oE "[0-9.]+$")
[ -z "$TPS" ] && TPS=$(printf '%s' "$TPS_LINE" | grep -oE "Mean TPS: [0-9.]+" | tail -1 | grep -oE "[0-9.]+$")
[ -z "$TPS" ] && fail "нет ответа RCON на «forge tps»: ${TPS_LINE:-пусто}"
grep -q "СТОРОЖ" "$LOG" && fail "сторож: сервер не тикает"
python3 -c "import sys; sys.exit(0 if float('$TPS') >= float('$MIN_TPS') else 1)" || fail "TPS $TPS < $MIN_TPS"

# Э0-02: аудит экономики — одна строка «Аудит: товары и деньги сходятся.»; при расхождении ответ многострочный
# («РАСХОЖДЕНИЕ:» + строки). Кириллица в ответе RCON может прийти «?», поэтому вторая проверка — по числу строк.
AUD=$(rcon "zone audit" 60 | sed 's/^zone audit -> //')
[ -z "$AUD" ] && fail "нет ответа RCON на «zone audit»"
if ! printf '%s\n' "$AUD" | grep -q "сходятся"; then
  [ "$(printf '%s\n' "$AUD" | grep -c .)" -gt 1 ] && fail "аудит экономики: расхождение — $(printf '%s' "$AUD" | head -3 | tr '\n' ' ')"
fi
echo "аудит: $(printf '%s' "$AUD" | head -1)"

rcon "save-all flush" >/dev/null || fail "RCON: save-all"
python3 -I tools/ci/rcon.py stop >/dev/null 2>&1 || true
for _ in $(seq 1 90); do kill -0 "$PID" 2>/dev/null || break; sleep 2; done
kill -0 "$PID" 2>/dev/null && fail "сервер не остановился за 3 мин после stop"
grep -qE "Saving worlds|All chunks are saved|All dimensions are saved" "$LOG" || fail "нет «Saving worlds» при остановке"
PID=""
# строка LevelRegions: «уровень l01_escape загружен: … x0/z0/w/d …» или «… не найден: нет файла …» (кириллица в логе может быть «?»)
grep -q "LevelRegions.*l01_escape.*x0/z0/w/d" "$LOG" || { echo "СЕРВЕР: ПРОВАЛ — мод не загрузил синтетический уровень l01_escape"; grep -m2 "LevelRegions" "$LOG" | cut -c1-300; exit 1; }
# Э0-02: лог за весь прогон (запуск, площадки, остановка) чист от Exception|ERROR|СТОРОЖ вне белого списка
ERRS=$(log_errors)
if [ -n "$ERRS" ]; then
  N=$(printf '%s\n' "$ERRS" | grep -c .)
  echo "— строки лога вне белого списка $ALLOW ($N):"; printf '%s\n' "$ERRS" | head -60 | cut -c1-300
  echo "СЕРВЕР: ПРОВАЛ — в логе $N строк с ошибками вне белого списка"; exit 1
fi
echo "СЕРВЕР: OK — мир «$WORLD» создан за $START с, TPS $TPS, 9 площадок + периметр загружены, аудит сходится, лог чист, сохранение при stop"
