#!/usr/bin/env bash
# Конвейер v2 (П-13, docs/claude/06-conveyor-v2.md): одна карточка = падающий тест + узкое задание.
# Исполнитель (дешёвая модель в Cline) делает тест зелёным — одна попытка, с потолком по времени и деньгам.
# Старшего Claude нет, правок по ревью нет, автослияния нет: PR сливает человек. Не вышло — PR «не вышло».
# Нужны: OPENROUTER_API_KEY, GH_TOKEN. Карточка: CARD (иначе — следующая по docs/tasks/README.md).
set -u
cd "$(git rev-parse --show-toplevel)"
AGENT_MODEL=${AGENT_MODEL:-deepseek/deepseek-v4.1-flash}
case "$AGENT_MODEL" in *v4-pro*) AGENT_MODEL=deepseek/deepseek-v4.1-flash;; esac   # V4 Pro в 12 раз дороже (31.10)
CARD_MINUTES=${CARD_MINUTES:-15}       # потолок времени исполнителя
CARD_BUDGET=${CARD_BUDGET:-0.30}       # потолок денег на карточку, $ (разница расхода ключа OpenRouter)
RETRIES=${CARD_RETRIES:-2}             # повторы запросов к модели внутри Cline (сеть, 429), не новые попытки
MIN_BALANCE=${MIN_BALANCE:-2}
tg() { python3 -I tools/notify/tg.py "$1" || true; }
RUN_URL="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"

CARD=${CARD:-$(python3 -I tools/conveyor/next_card.py)}
[ -z "$CARD" ] && { echo "Нет карточки с меткой «Конвейер: да», готовой к выполнению."; tg "💤 Конвейер: нет готовых карточек"; exit 0; }
FILE=$(ls docs/tasks/${CARD}-*.md 2>/dev/null | head -1)
[ -z "$FILE" ] && { echo "::error::нет файла карточки $CARD"; exit 1; }
# паспорт карточки: метка, тест, разрешённые файлы (tools/conveyor/card.py проверяет формат)
PASSPORT=$(python3 -I tools/conveyor/card.py "$FILE" --shell) || { echo "::error title=Карточка $CARD::не для конвейера или неверный паспорт"; tg "⛔ $CARD: карточка не для конвейера (нужны «Конвейер: да», «Тест:», «Файлы:»)"; exit 1; }
eval "$PASSPORT"
TITLE=$(head -1 "$FILE" | sed -E 's/^# *([0-9]{3}\. *)?//')

read U0 LEFT <<< "$(python3 -I tools/notify/balance.py)"
if [ "$LEFT" != "-" ] && python3 -c "import sys; sys.exit(0 if float('$LEFT') < float('$MIN_BALANCE') else 1)"; then
  echo "::warning title=Бюджет::остаток OpenRouter \$$LEFT < \$$MIN_BALANCE — конвейер на паузе"
  tg "⚠️ Доступно на OpenRouter \$$LEFT (порог \$$MIN_BALANCE) — карточка $CARD не начата."
  exit 0
fi

git config user.name "stalkerzone-conveyor"; git config user.email "conveyor@users.noreply.github.com"
BR="task/${CARD}-auto"
git ls-remote --exit-code --heads origin "$BR" >/dev/null 2>&1 && { echo "Ветка $BR уже есть — карточка в работе или ждёт слияния."; exit 0; }
git checkout -q -B "$BR" origin/main

# 1. Тест карточки кладётся в src/test/java и обязан ПАДАТЬ до работы — иначе карточка пустая, деньги не тратим.
mkdir -p src/test/java
cp "$CARD_TEST_SRC" "src/test/java/$CARD_TEST.java"
bash tools/conveyor/check.sh --only "$CARD_TEST" > /tmp/before.txt 2>&1
if grep -q "^Тесты симуляции: все прошли" /tmp/before.txt; then
  echo "::error title=Карточка $CARD::тест $CARD_TEST проходит ещё до работы — карточка не нужна или тест не ловит ошибку"
  tg "⛔ $CARD: тест $CARD_TEST зелёный ещё до работы — карточку вернуть «Программисту»"; exit 1
fi
grep -q "КОМПИЛЯЦИЯ ТЕСТОВ НЕ ПРОШЛА" /tmp/before.txt && echo "Тест не компилируется — это тоже «красный»: исполнитель добавит нужный код."

echo "::notice title=Карточка $CARD::$TITLE (модель $AGENT_MODEL, потолок $CARD_MINUTES мин / \$$CARD_BUDGET)"
tg "▶️ $TITLE — старт ($AGENT_MODEL, потолок $CARD_MINUTES мин / \$$CARD_BUDGET)"
cline --config /tmp/cfg-agent auth -p openrouter -k "$OPENROUTER_API_KEY" -m "$AGENT_MODEL" >/dev/null

{ echo "Выполни карточку $FILE — сделай тест src/test/java/$CARD_TEST.java зелёным, не трогая сам тест.";
  echo; echo "Читать и менять можно ТОЛЬКО эти файлы (остальной репозиторий не открывай):"; printf '%s\n' $CARD_FILES | sed 's/^/- /';
  echo "- docs/tasks/README.md (только статус своей карточки)"; [ -n "$CARD_CHECKLIST" ] && echo "- $CARD_CHECKLIST (новый файл пунктов проверки в игре)";
  echo; cat tools/conveyor/rules.txt; echo; echo "## Карточка"; cat "$FILE"; } > /tmp/pA.txt

# 2. Одна попытка исполнителя; сторож денег убивает его при перерасходе.
T0=$(date +%s)
( cat /tmp/pA.txt | timeout $((CARD_MINUTES * 60)) cline --config /tmp/cfg-agent -y -t $((CARD_MINUTES * 60)) --retries "$RETRIES" \
    "Выполни задание из входных данных." > /tmp/cline.log 2>&1 ) &
AGENT=$!
OVER=""
while kill -0 "$AGENT" 2>/dev/null; do
  sleep 30
  read U _ <<< "$(python3 -I tools/notify/balance.py)"
  if [ "$U0" != "-" ] && [ "$U" != "-" ] && python3 -c "import sys; sys.exit(0 if float('$U')-float('$U0') > float('$CARD_BUDGET') else 1)"; then
    OVER="бюджет \$$CARD_BUDGET превышен"; pkill -P "$AGENT" 2>/dev/null; kill "$AGENT" 2>/dev/null; break
  fi
done
wait "$AGENT" 2>/dev/null; RC=$?
MINUTES=$(( ($(date +%s) - T0 + 59) / 60 ))
[ -z "$OVER" ] && [ "$RC" = 124 ] && OVER="время $CARD_MINUTES мин вышло"
sed 's/\x1b\[[0-9;]*m//g' /tmp/cline.log | tail -30
ESC=$(head -c 3000 ESCALATE.md 2>/dev/null); rm -f ESCALATE.md REVIEW_REPLY.md

# 3. Проверка: тест карточки зелёный, всё остальное зелёное, тест не тронут, чужие файлы не тронуты.
WHY=""
cmp -s "$CARD_TEST_SRC" "src/test/java/$CARD_TEST.java" || { WHY="исполнитель изменил тест карточки"; cp "$CARD_TEST_SRC" "src/test/java/$CARD_TEST.java"; }
STRAY=$(python3 -I tools/conveyor/card.py "$FILE" --stray "$(git -c core.quotePath=false status --porcelain --untracked-files=all | cut -c4-)")
[ -n "$STRAY" ] && WHY="${WHY:+$WHY; }изменены файлы вне карточки: $(echo $STRAY)"
bash tools/conveyor/check.sh > /tmp/check.txt 2>&1 || WHY="${WHY:+$WHY; }проверка: $(head -1 /tmp/check.txt)"
[ -n "$OVER" ] && WHY="${WHY:+$WHY; }$OVER"
read U1 _ <<< "$(python3 -I tools/notify/balance.py)"
COST=$(python3 -c "u0,u1='$U0','$U1'; print('-' if '-' in (u0,u1) else f'{float(u1)-float(u0):.2f}')")
OK=$([ -z "$WHY" ] && echo 1 || echo 0)

git add -A -- src docs tools 2>/dev/null
git commit -q -m "$CARD: $TITLE (конвейер v2, $AGENT_MODEL)" || { tg "❌ $CARD: исполнитель ничего не изменил ($OVER) — $RUN_URL"; WHY="${WHY:-ничего не изменено}"; git commit -q --allow-empty -m "$CARD: не вышло"; }
git push -q -u origin "$BR" || { tg "❌ $CARD: пуш ветки отклонён — $RUN_URL"; exit 1; }

# 4. PR: «готов к просмотру» или «не вышло». Метрики — машиночитаемой строкой (их собирает tools/conveyor/metrics.py).
METRICS="conveyor-metrics: card=$CARD ok=$OK cost=$COST minutes=$MINUTES attempts=1 model=$AGENT_MODEL"
{ echo "Карточка \`$FILE\` — конвейер v2 (одна попытка, без старшего, без автослияния)."; echo;
  if [ "$OK" = 1 ]; then echo "✅ Тест \`$CARD_TEST\` зелёный, остальные тесты и сборка — тоже. **Сливает человек** после взгляда «Программиста»."
  else echo "⚠️ **Не вышло:** $WHY. Карточка возвращается «Программисту»: раздробить или сделать в чате.";
    [ -n "$ESC" ] && { echo; echo "**Исполнитель (ESCALATE.md):**"; echo; echo "$ESC"; }; fi
  echo; echo "Время: $MINUTES мин · стоимость OpenRouter: \$$COST · модель: $AGENT_MODEL"; echo;
  echo '```'; tail -25 /tmp/check.txt; echo '```'; echo; echo "<!-- $METRICS -->"; echo;
  echo "🤖 Generated with [Claude Code](https://claude.com/claude-code)"; } > /tmp/body.md
gh label create conveyor --color 1D76DB --description "PR конвейера v2" 2>/dev/null || true
if [ "$OK" = 1 ]; then
  URL=$(gh pr create --head "$BR" --base main --label conveyor --title "$CARD: $TITLE" --body-file /tmp/body.md)
  # дешёвое ревью — только подсказка в комментарии, на слияние не влияет
  BASE_SHA=$(git merge-base HEAD origin/main) HEAD_REF="$BR" PR_BODY="КОНВЕЙЕР" python3 -I tools/review/ai_review.py > /tmp/review.md 2>/dev/null \
    && ! grep -qE "REVIEW_UNAVAILABLE|пропущено" /tmp/review.md && gh pr comment "$URL" --body-file /tmp/review.md >/dev/null
  tg "✅ $TITLE — PR ждёт просмотра: $URL
$MINUTES мин, \$$COST"
else
  gh label create "не вышло" --color B60205 --description "Конвейер v2 не справился — карточка возвращается Программисту" 2>/dev/null || true
  URL=$(gh pr create --head "$BR" --base main --draft --label conveyor --label "не вышло" --title "$CARD: $TITLE — не вышло" --body-file /tmp/body.md)
  tg "⚠️ $CARD не вышло: $WHY
$URL ($MINUTES мин, \$$COST)"
fi
echo "PR: $URL"
echo "$METRICS"
# пуш токеном Actions не запускает другие workflow — сборку и сервер на ветке запускаем сами
gh workflow run build.yml --ref "$BR" 2>/dev/null || true
[ "$OK" = 1 ]
