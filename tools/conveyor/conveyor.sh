#!/usr/bin/env bash
# Конвейер карточек: исполнитель (дешёвая модель) -> проверка -> старший (сильная модель) при провале -> ревью -> PR.
# Нужны: OPENROUTER_API_KEY, GH_TOKEN. Модели: AGENT_MODEL, STRONG_MODEL. Карточка: CARD (или выбирается сама).
set -u
cd "$(git rev-parse --show-toplevel)"
AGENT_MODEL=${AGENT_MODEL:-deepseek/deepseek-v4.1-flash}
AGENT_MODEL_STRONG=${AGENT_MODEL_STRONG:-deepseek/deepseek-v4-pro}
STRONG_MODEL=${STRONG_MODEL:-anthropic/claude-sonnet-5-5}
# старшая модель — через ProxyAPI (OpenAI-совместимый адрес), ключ PROXYAPI_KEY
STRONG_BASE_URL=${STRONG_BASE_URL:-https://api.proxyapi.ru/v1}
# уведомления в Telegram (карточка 017); без секретов — молча пропуск, ошибки не роняют конвейер
tg() { python3 -I tools/notify/tg.py "$1" || true; }
RUN_URL="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"
CARD=${CARD:-$(python3 -I tools/conveyor/next_card.py)}
[ -z "$CARD" ] && { echo "Нет карточки, готовой к выполнению."; tg "💤 Конвейер (${LANE:-?}): нет готовых карточек"; exit 0; }
# предохранитель бюджета (карточка 018): мало денег на OpenRouter — не начинаем
read U0 LEFT <<< "$(python3 -I tools/notify/balance.py)"
MIN_BALANCE=${MIN_BALANCE:-2}
if [ "$LEFT" != "-" ] && python3 -c "import sys; sys.exit(0 if float('$LEFT') < float('$MIN_BALANCE') else 1)"; then
  echo "::warning title=Бюджет::остаток OpenRouter \$$LEFT < \$$MIN_BALANCE — конвейер на паузе"
  tg "⚠️ Баланс OpenRouter \$$LEFT (порог \$$MIN_BALANCE) — конвейер на паузе, карточка $CARD не начата. Пополните баланс и запустите conveyor вручную."
  exit 0
fi
FILE=$(ls docs/tasks/${CARD}-*.md | head -1)
TITLE=$(head -1 "$FILE" | sed 's/^# *//')
grep -q "Модель исполнителя: сильная" "$FILE" && AGENT_MODEL=$AGENT_MODEL_STRONG
BR="task/${CARD}-auto"
echo "::notice title=Карточка $CARD::$TITLE (модель $AGENT_MODEL)"
tg "▶️ $TITLE — старт ($AGENT_MODEL)"
git checkout -q -b "$BR"
git config user.name "stalkerzone-conveyor"; git config user.email "conveyor@users.noreply.github.com"
cline --config /tmp/cfg-agent auth -p openrouter -k "$OPENROUTER_API_KEY" -m "$AGENT_MODEL" >/dev/null
if [ -n "${PROXYAPI_KEY:-}" ]; then
  cline --config /tmp/cfg-strong auth -p openai -k "$PROXYAPI_KEY" -m "$STRONG_MODEL" -b "$STRONG_BASE_URL" >/dev/null
else
  echo "PROXYAPI_KEY не задан — старшая модель недоступна"
fi
RULES='Ты работаешь в конвейере без человека. НЕ делай git commit, push и PR — это делает конвейер.
Проверка: `bash tools/conveyor/check.sh` (тесты симуляции и сборка; печатает итог и хвост ошибок). Не читай большие логи целиком.
Simulation.java и другие большие файлы читай кусками (поиск, затем диапазон строк).
Если одна и та же ошибка не уходит после 3 попыток исправления — ОСТАНОВИСЬ: опиши проблему в файле ESCALATE.md
(что делал, текст ошибки, гипотезы) и заверши работу. Дальше задачу подхватит старший разработчик.
Смоук-тест сервера (tools/smoketest.ps1) в конвейере НЕ запускай: здесь Linux и нет файла уровня — проверяет архитектор на вехе.
Не меняй файлы в .github/workflows: токен конвейера не может их запушить, работа пропадёт.
Временные файлы клади в /tmp, не в репозиторий. В конце поставь карточке статус «готово (конвейер)» в docs/tasks/README.md.'
run() { # роль (agent|strong), таймаут, файл с заданием
  if [ "$1" = strong ] && [ -z "${PROXYAPI_KEY:-}" ]; then echo "старший недоступен — пропуск"; return; fi
  echo "::group::cline $1"; cat "$3" | cline --config "/tmp/cfg-$1" -y -t "$2" --retries 6 "Выполни задание из входных данных." >/tmp/cline-$$.log 2>&1
  rc=$?; tail -30 /tmp/cline-$$.log; echo "::endgroup::"
  # краткий итог в аннотацию (её видно в API без доступа к логам)
  echo "::notice title=cline $1 rc=$rc::$(sed 's/\x1b\[[0-9;]*m//g' /tmp/cline-$$.log | tail -c 900 | tr '\n\r' '  ' | sed 's/::/ : /g')"
}
PHASES="исполнитель ($AGENT_MODEL)"
{ echo "Выполни карточку $FILE. Сначала прочитай её и .clinerules (правила про git/PR выше в этом задании важнее)."; echo; echo "$RULES"; } > /tmp/pA.txt
run agent 3600 /tmp/pA.txt
bash tools/conveyor/check.sh > /tmp/check1.txt 2>&1; ok=$?
tg "$([ $ok -eq 0 ] && echo ✅ || echo ❌) $CARD: исполнитель закончил, проверка: $(head -1 /tmp/check1.txt)"
if [ $ok -ne 0 ]; then
  PHASES="$PHASES → старший ($STRONG_MODEL)"
  tg "🔁 $CARD: тесты не прошли, подключаю старшего ($STRONG_MODEL)
$(tail -5 /tmp/check1.txt)"
  { echo "Ты старший разработчик. Младший агент выполнял карточку $FILE и не справился. Не переделывай с нуля — найди причину и исправь минимально.";
    echo; echo "$RULES"; echo; echo "## ESCALATE.md от младшего"; cat ESCALATE.md 2>/dev/null || echo "(нет)";
    echo; echo "## Изменённые файлы"; git status --short | head -50; echo; echo "## Результат проверки"; tail -80 /tmp/check1.txt; } > /tmp/pB.txt
  run strong 1200 /tmp/pB.txt
  bash tools/conveyor/check.sh > /tmp/check1.txt 2>&1; ok=$?
fi
rm -f ESCALATE.md
git add -A -- src docs tools .github .gitignore 2>/dev/null
git commit -q -m "$CARD: $TITLE (конвейер: $PHASES)" || { echo "::error title=Агент ничего не изменил::карточка $CARD; см. аннотацию cline"; tg "❌ $CARD: агент ничего не изменил — $RUN_URL"; exit 1; }
REVIEW=""
if [ $ok -eq 0 ]; then
  git push -q -u origin "$BR"
  BASE_SHA=$(git merge-base HEAD origin/main) HEAD_REF="$BR" python3 -I tools/review/ai_review.py > /tmp/review.md
  grep -qE "Вердикт:\*\*\s*ОК" /tmp/review.md && ! grep -q "### Блокеры" /tmp/review.md && tg "🔎 $CARD: ревью — ОК"
  grep -q REVIEW_UNAVAILABLE /tmp/review.md && tg "🔎 $CARD: ревью моделью недоступно — старшего не зову, смотрите PR"
  if grep -q "### Блокеры" /tmp/review.md || { ! grep -q REVIEW_UNAVAILABLE /tmp/review.md && ! grep -qE "Вердикт:\*\*\s*ОК" /tmp/review.md; }; then
    PHASES="$PHASES → старший по ревью"
    tg "🔎 $CARD: ревью — есть замечания, правит старший"
    { echo "Ты старший разработчик. По карточке $FILE работа сделана, тесты зелёные, но ревьюер нашёл замечания.";
      echo "Исправь обоснованные замечания минимально; необоснованные — пропусти и кратко объясни почему в файле REVIEW_REPLY.md."; echo; echo "$RULES"; echo; cat /tmp/review.md; } > /tmp/pC.txt
    run strong 900 /tmp/pC.txt
    bash tools/conveyor/check.sh > /tmp/check1.txt 2>&1; ok=$?
    REPLY=$(cat REVIEW_REPLY.md 2>/dev/null); rm -f REVIEW_REPLY.md
    git add -A -- src docs tools .github .gitignore; git commit -q -m "$CARD: правки по ревью" || true
    BASE_SHA=$(git merge-base HEAD origin/main) HEAD_REF="$BR" python3 -I tools/review/ai_review.py > /tmp/review.md
    tg "$([ $ok -eq 0 ] && echo ✅ || echo ❌) $CARD: старший поправил по ревью, проверка: $(head -1 /tmp/check1.txt)"
    [ -n "$REPLY" ] && printf '\n\n### Ответ старшего на ревью\n%s\n' "$REPLY" >> /tmp/review.md
  fi
fi
# стоимость карточки (карточка 018): разница расхода ключа OpenRouter; при двух линиях сразу — сумма обеих
read U1 LEFT1 <<< "$(python3 -I tools/notify/balance.py)"
COST=$(python3 -c "u0,u1='$U0','$U1'; print('—' if '-' in (u0,u1) else f'\${float(u1)-float(u0):.2f}')")
COST_LINE="Стоимость OpenRouter: $COST (за время карточки; если работали обе линии — их сумма), остаток \$$LEFT1"
git push -q -u origin "$BR" || { echo "::error title=Пуш отклонён::ветка $BR"; tg "❌ $CARD: пуш ветки отклонён, работа не сохранена — $RUN_URL"; exit 1; }
{ echo "Карточка \`$FILE\` выполнена конвейером."; echo; echo "**Этапы:** $PHASES"; echo; echo "**Проверка (тесты симуляции + сборка):**";
  echo '```'; tail -40 /tmp/check1.txt; echo '```';
  [ $ok -ne 0 ] && echo && echo "⚠️ Проверка не прошла даже после старшего — нужен человек (@Grilik): перешлите ссылку архитектору.";
  echo; echo "**$COST_LINE**"; echo; echo "🤖 Generated with [Claude Code](https://claude.com/claude-code)"; } > /tmp/body.md
if [ $ok -eq 0 ]; then
  URL=$(gh pr create --head "$BR" --base main --title "$CARD: $TITLE" --body-file /tmp/body.md) || {
    echo "::error title=PR не создан::Включите Settings → Actions → General → Allow GitHub Actions to create and approve pull requests. Ветка $BR запушена."
    tg "⚠️ $CARD: работа готова (ветка $BR), но PR не создан — нужен человек. $RUN_URL"; exit 1; }
  tg "✅ $TITLE — PR готов к слиянию: $URL
Этапы: $PHASES
$COST_LINE"
else
  gh label create needs-human --color B60205 --description "Конвейер не справился" 2>/dev/null || true
  URL=$(gh pr create --head "$BR" --base main --draft --label needs-human --title "$CARD: $TITLE — нужен человек" --body-file /tmp/body.md)
  tg "⚠️ $CARD: нужен человек — $URL
$(tail -5 /tmp/check1.txt)
$RUN_URL"
fi
echo "PR: $URL"
[ -f /tmp/review.md ] && gh pr comment "$URL" --body-file /tmp/review.md
gh workflow run build.yml --ref "$BR" 2>/dev/null || true
exit $ok
