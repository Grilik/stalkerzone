#!/usr/bin/env bash
# Проверка конвейера (Linux): тесты симуляции + сборка мода. Полный лог — /tmp/sz-check.log, на экран — итог и хвост.
#   bash tools/conveyor/check.sh               — все тесты и сборка
#   bash tools/conveyor/check.sh --only XTest  — один тест, без сборки (быстро, для исполнителя)
# Первая строка вывода — итог. Код выхода 0 — всё зелёное.
cd "$(dirname "$0")/../.."
ONLY=""; [ "${1:-}" = "--only" ] && ONLY=${2:-}
LOG=/tmp/sz-check.log; : > "$LOG"; fail=0
rm -rf /tmp/sz-out && mkdir -p /tmp/sz-out
if ! javac --release 17 -encoding UTF-8 -d /tmp/sz-out $(bash tools/ci/test_sources.sh) >>"$LOG" 2>&1; then
  echo "КОМПИЛЯЦИЯ ТЕСТОВ НЕ ПРОШЛА"; grep -m20 -E "error:" -A2 "$LOG"; exit 1
fi
failed=""
for f in src/test/java/*Test.java; do
  t=$(basename "$f" .java)
  [ -n "$ONLY" ] && [ "$t" != "$ONLY" ] && continue
  if ! timeout 600 java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp /tmp/sz-out "$t" >"/tmp/sz-$t.log" 2>&1; then
    failed="$failed $t"; fail=1
    { echo "=== FAIL $t"; grep -m20 -E "FAIL|Exception|Error|at ru\.stalkerzone" "/tmp/sz-$t.log"; tail -5 "/tmp/sz-$t.log"; } >>"$LOG"
  fi
done
if [ -n "$failed" ]; then echo "УПАЛИ ТЕСТЫ:$failed"; else echo "Тесты симуляции: все прошли"; fi
if [ -z "$ONLY" ]; then
  if ! ./gradlew build --no-daemon -q >/tmp/sz-gradle.log 2>&1; then
    fail=1; echo "СБОРКА НЕ ПРОШЛА"; grep -E "error:|FAILED|What went wrong" -A3 /tmp/sz-gradle.log | head -40 >>"$LOG"
  else echo "Сборка: OK"; fi
fi
[ $fail -ne 0 ] && tail -60 "$LOG"
exit $fail
