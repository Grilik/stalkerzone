#!/usr/bin/env bash
# Конвейер карточек: исполнитель (дешёвая модель) -> проверка -> старший (сильная модель) при провале -> ревью -> PR.
# Нужны: OPENROUTER_API_KEY, GH_TOKEN. Модели: AGENT_MODEL, STRONG_MODEL. Карточка: CARD (или выбирается сама).
set -u
cd "$(git rev-parse --show-toplevel)"
AGENT_MODEL=${AGENT_MODEL:-deepseek/deepseek-v4.1-flash}
# экономия (31.10): V4 Pro стоил ~$2 за карточку против ~$0.16 у Flash, а V4.1 Flash не слабее V4 Pro.
# Карточки с меткой «сильная» тоже идут на Flash; при провале их добивает Claude по подписке.
# V4 Pro из переменной репозитория игнорируется — вернуть можно только правкой этой строки.
AGENT_MODEL_STRONG=${AGENT_MODEL_STRONG:-$AGENT_MODEL}
case "$AGENT_MODEL_STRONG" in *v4-pro*) AGENT_MODEL_STRONG=$AGENT_MODEL;; esac
STRONG_MODEL=${STRONG_MODEL:-anthropic/claude-sonnet-5-5}
# старшая модель — через ProxyAPI (OpenAI-совместимый адрес), ключ PROXYAPI_KEY
STRONG_BASE_URL=${STRONG_BASE_URL:-https://api.proxyapi.ru/v1}
# уведомления в Telegram (карточка 017); без секретов — молча пропуск, ошибки не роняют конвейер
tg() { python3 -I tools/notify/tg.py "$1" || true; }
RUN_URL="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"
CARD_GIVEN=${CARD:-}
CARD=${CARD:-$(python3 -I tools/conveyor/next_card.py)}
[ -z "$CARD" ] && { echo "Нет карточки, готовой к выполнению."; tg "💤 Конвейер (${LANE:-?}): нет готовых карточек"; exit 0; }
# предохранитель бюджета (карточка 018): мало денег на OpenRouter — не начинаем
read U0 LEFT <<< "$(python3 -I tools/notify/balance.py)"
MIN_BALANCE=${MIN_BALANCE:-2}
if [ "$LEFT" != "-" ] && python3 -c "import sys; sys.exit(0 if float('$LEFT') < float('$MIN_BALANCE') else 1)"; then
  echo "::warning title=Бюджет::остаток OpenRouter \$$LEFT < \$$MIN_BALANCE — конвейер на паузе"
  tg "⚠️ Доступно на OpenRouter \$$LEFT ($(python3 -I tools/notify/balance.py --text); порог \$$MIN_BALANCE) — конвейер на паузе, карточка $CARD не начата. Пополните счёт или поднимите лимит ключа (openrouter.ai/keys) и запустите conveyor вручную."
  exit 0
fi
git config user.name "stalkerzone-conveyor"; git config user.email "conveyor@users.noreply.github.com"
# захват карточки: две линии могут выбрать одну карточку одновременно. Каждая пушит в task/NNN-auto
# свой пустой коммит — второй пуш отклоняется (не fast-forward), и линия берёт следующую карточку.
EXPLICIT=${CARD_GIVEN:-}
SKIP_CARDS=""
for try in 1 2 3; do
  BR="task/${CARD}-auto"
  git checkout -q -B "$BR" origin/main 2>/dev/null || git checkout -q -B "$BR"
  git commit -q --allow-empty -m "$CARD: захват линией ${LANE:-?} (${GITHUB_RUN_ID:-local})"
  CLAIM_SHA=$(git rev-parse HEAD)
  if git push -q origin "HEAD:refs/heads/$BR" 2>/tmp/claim.txt; then break; fi
  echo "::notice title=Захват::карточку $CARD уже взяла другая линия"
  [ -n "$EXPLICIT" ] && { tg "⚠️ $CARD: уже в работе у другой линии"; exit 0; }
  SKIP_CARDS="$SKIP_CARDS $CARD"; CARD=$(SKIP_CARDS="$SKIP_CARDS" python3 -I tools/conveyor/next_card.py)
  [ -z "$CARD" ] && { echo "Свободных карточек нет."; exit 0; }
done
# если запуск оборвётся до работы, снимаем захват, чтобы карточка не зависла
release() { [ "$(git ls-remote origin "refs/heads/$BR" | cut -f1)" = "$CLAIM_SHA" ] && git push -q origin --delete "$BR" 2>/dev/null; true; }
trap release EXIT
FILE=$(ls docs/tasks/${CARD}-*.md | head -1)
TITLE=$(head -1 "$FILE" | sed 's/^# *//')
grep -q "Модель исполнителя: сильная" "$FILE" && AGENT_MODEL=$AGENT_MODEL_STRONG
echo "::notice title=Карточка $CARD::$TITLE (модель $AGENT_MODEL, линия ${LANE:-?})"
tg "▶️ $TITLE — старт ($AGENT_MODEL, линия ${LANE:-?})"
cline --config /tmp/cfg-agent auth -p openrouter -k "$OPENROUTER_API_KEY" -m "$AGENT_MODEL" >/dev/null
# старший (карточка 019): Claude Code по подписке, если задан CLAUDE_CODE_OAUTH_TOKEN; иначе — Claude через ProxyAPI
CLAUDE_DAILY_LIMIT=${CLAUDE_DAILY_LIMIT:-5}
if [ -n "${CLAUDE_CODE_OAUTH_TOKEN:-}" ]; then
  STRONG_NAME="Claude Code"
  # сколько раз старший-Claude уже звался сегодня (по этапам в PR конвейера за сутки МСК)
  SINCE=$(date -u -d "$(TZ=Europe/Moscow date +%Y-%m-%d) 00:00 +0300" +%Y-%m-%dT%H:%M:%SZ)
  # ручное обнуление счётчика: вызовы раньше CLAUDE_RESET_AT не считаются (31.10 — сброс после смены правил ревью)
  CLAUDE_RESET_AT=${CLAUDE_RESET_AT:-2026-10-31T08:00:00Z}
  [[ "$CLAUDE_RESET_AT" > "$SINCE" ]] && SINCE=$CLAUDE_RESET_AT
  CLAUDE_USED=$(gh api "repos/${GITHUB_REPOSITORY:-Grilik/stalkerzone}/pulls?state=all&per_page=50" \
    --jq "[.[] | select(.created_at >= \"$SINCE\") | (.body // \"\") | scan(\"\\(Claude Code\\)\")] | length" 2>/dev/null)
  [[ "$CLAUDE_USED" =~ ^[0-9]+$ ]] || CLAUDE_USED=0
  echo "Claude Code сегодня: ${CLAUDE_USED:-0} из $CLAUDE_DAILY_LIMIT"
else
  STRONG_NAME="$STRONG_MODEL"
fi
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
  if [ "$1" = strong ] && [ -n "${CLAUDE_CODE_OAUTH_TOKEN:-}" ]; then
    if [ "${CLAUDE_USED:-0}" -ge "$CLAUDE_DAILY_LIMIT" ]; then
      echo "::warning title=Claude Code::дневной лимит вызовов ($CLAUDE_DAILY_LIMIT) исчерпан"; tg "⏸ $CARD: лимит вызовов Claude на сегодня ($CLAUDE_DAILY_LIMIT) исчерпан — карточка уйдёт человеку"; return
    fi
    CLAUDE_USED=$(( ${CLAUDE_USED:-0} + 1 ))
    tg "🧠 $CARD: зову Claude Code (вызов $CLAUDE_USED из $CLAUDE_DAILY_LIMIT за сегодня)"
    echo "::group::claude $1"
    timeout "$2" claude -p "$(cat "$3")" --model sonnet --max-turns 80 --permission-mode acceptEdits \
      --allowedTools "Bash,Read,Edit,Write,Glob,Grep" >/tmp/claude-$$.log 2>&1
    rc=$?; tail -30 /tmp/claude-$$.log; echo "::endgroup::"
    echo "::notice title=claude rc=$rc::$(tail -c 900 /tmp/claude-$$.log | tr '\n\r' '  ' | sed 's/::/ : /g')"
    grep -qiE "usage limit|rate limit|limit reached" /tmp/claude-$$.log && tg "⚠️ $CARD: Claude Code упёрся в лимит подписки"
    return
  fi
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
  PHASES="$PHASES → старший ($STRONG_NAME)"
  tg "🔁 $CARD: тесты не прошли, подключаю старшего ($STRONG_NAME)
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
  BASE_SHA=$(git merge-base HEAD origin/main) HEAD_REF="$BR" PR_BODY="КОНВЕЙЕР" python3 -I tools/review/ai_review.py > /tmp/review.md
  grep -qE "Вердикт:\*\*\s*ОК" /tmp/review.md && ! grep -q "### Блокеры" /tmp/review.md && tg "🔎 $CARD: ревью — ОК"
  grep -q REVIEW_UNAVAILABLE /tmp/review.md && tg "🔎 $CARD: ревью моделью недоступно — старшего не зову, смотрите PR"
  if grep -q "### Блокеры" /tmp/review.md || { ! grep -q REVIEW_UNAVAILABLE /tmp/review.md && ! grep -qE "Вердикт:\*\*\s*ОК" /tmp/review.md; }; then
    # карточка 022: «ЕСТЬ ЗАМЕЧАНИЯ» правит дешёвый исполнитель; Claude — только для «БЛОКЕР» и механических блокеров
    if grep -q "### Блокеры" /tmp/review.md || grep -qE "Вердикт:\*\*\s*БЛОКЕР" /tmp/review.md; then WHO=strong; WHO_NAME=$STRONG_NAME; else WHO=agent; WHO_NAME=$AGENT_MODEL; fi
    PHASES="$PHASES → правки по ревью ($WHO_NAME)"
    tg "🔎 $CARD: ревью — есть замечания, правит $WHO_NAME"
    { echo "Ты разработчик. По карточке $FILE работа сделана, тесты зелёные, но ревьюер нашёл замечания.";
      echo "Исправь обоснованные замечания минимально; необоснованные — пропусти и кратко объясни почему в файле REVIEW_REPLY.md."; echo; echo "$RULES"; echo; cat /tmp/review.md; } > /tmp/pC.txt
    run $WHO 900 /tmp/pC.txt
    bash tools/conveyor/check.sh > /tmp/check1.txt 2>&1; ok=$?
    REPLY=$(cat REVIEW_REPLY.md 2>/dev/null); rm -f REVIEW_REPLY.md
    git add -A -- src docs tools .github .gitignore; git commit -q -m "$CARD: правки по ревью" || true
    BASE_SHA=$(git merge-base HEAD origin/main) HEAD_REF="$BR" PR_BODY="КОНВЕЙЕР" python3 -I tools/review/ai_review.py > /tmp/review.md
    tg "$([ $ok -eq 0 ] && echo ✅ || echo ❌) $CARD: правки по ревью внесены, проверка: $(head -1 /tmp/check1.txt)"
    [ -n "$REPLY" ] && printf '\n\n### Ответ исполнителя на ревью\n%s\n' "$REPLY" >> /tmp/review.md
  fi
fi
# стоимость карточки (карточка 018): разница расхода ключа OpenRouter; при двух линиях сразу — сумма обеих
read U1 LEFT1 <<< "$(python3 -I tools/notify/balance.py)"
COST=$(python3 -c "u0,u1='$U0','$U1'; print('—' if '-' in (u0,u1) else f'\${float(u1)-float(u0):.2f}')")
COST_LINE="Стоимость OpenRouter: $COST (за время карточки; если работали обе линии — их сумма). Остаток: $(python3 -I tools/notify/balance.py --text)"
# --- карточка 020: подтянуть свежий main (другая линия могла слить своё) и починить конфликты ---
sync_main() {
  git fetch -q origin main
  local before; before=$(git rev-parse HEAD)
  if git merge -q --no-edit origin/main >/tmp/merge.txt 2>&1; then
    [ "$(git rev-parse HEAD)" = "$before" ] && return 0
  else
    local files; files=$(git diff --name-only --diff-filter=U | tr '\n' ' ')
    [ -z "${files// /}" ] && { git merge --abort 2>/dev/null; git reset -q --hard "$before"; return 1; }
    tg "🔀 $CARD: конфликт со свежим main ($files) — чиню"
    PHASES="$PHASES → конфликт с main"
    { echo "В ветке карточки $FILE идёт слияние свежего main, конфликты в файлах: $files";
      echo "Разреши конфликты, сохранив смысл ОБЕИХ сторон: изменения карточки и изменения из main (обычно нужно оставить и то и другое)."
      echo "Убери все маркеры <<<<<<< ======= >>>>>>>. НЕ делай git commit/merge/abort. Затем запусти проверку."; echo; echo "$RULES"; } > /tmp/pM.txt
    run agent 1500 /tmp/pM.txt
    if grep -qE '^(<<<<<<<|>>>>>>>)' $files 2>/dev/null || ! bash tools/conveyor/check.sh > /tmp/check1.txt 2>&1; then
      PHASES="$PHASES → старший по конфликту ($STRONG_NAME)"; run strong 1200 /tmp/pM.txt
    fi
    if grep -qE '^(<<<<<<<|>>>>>>>)' $files 2>/dev/null; then
      git merge --abort 2>/dev/null; git reset -q --hard "$before"; return 1
    fi
    rm -f ESCALATE.md REVIEW_REPLY.md
    git add -A -- $(ls -d src docs tools .gitignore 2>/dev/null)
    git commit -q --no-edit || { git merge --abort 2>/dev/null; git reset -q --hard "$before"; return 1; }
  fi
  # main изменился — перепроверка
  if ! bash tools/conveyor/check.sh > /tmp/check1.txt 2>&1; then git reset -q --hard "$before"; return 1; fi
  return 0
}
SYNC_OK=1
if [ $ok -eq 0 ]; then
  sync_main || { SYNC_OK=0; bash tools/conveyor/check.sh > /tmp/check1.txt 2>&1; tg "⚠️ $CARD: не удалось совместить со свежим main — автослияния не будет"; }
fi
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
# --- карточка 020: автослияние (тесты зелёные, ревью «ОК», не веха, main совмещён) ---
if [ $ok -eq 0 ]; then
  why=""
  case "$CARD" in 006|011|015) why="веха — ждёт проверки";; esac
  [ "${AUTO_MERGE:-1}" = 0 ] && why="автослияние выключено (AUTO_MERGE=0)"
  [ $SYNC_OK -eq 1 ] || why="${why:-конфликт со свежим main не решён}"
  grep -q REVIEW_UNAVAILABLE /tmp/review.md 2>/dev/null && why="${why:-ревью моделью недоступно}"
  # после раунда правок «ЕСТЬ ЗАМЕЧАНИЯ» не мешает автослиянию (ревьюер придирчив); мешают только блокеры
  { grep -q "### Блокеры" /tmp/review.md || grep -qE "Вердикт:\*\*\s*БЛОКЕР" /tmp/review.md; } 2>/dev/null && why="${why:-ревью: блокер}"
  { grep -qE "Вердикт:\*\*\s*(ОК|ЕСТЬ ЗАМЕЧАНИЯ)" /tmp/review.md; } 2>/dev/null || why="${why:-ревью без вердикта}"
  if [ -z "$why" ]; then
    if gh pr merge "$URL" --squash --delete-branch >/tmp/pm.txt 2>&1; then
      tg "🤖✅ $CARD слит автоматически: $URL"
      # слияние токеном Actions не запускает другие workflow — запускаем сами: сборку main (релиз 🎮) и следующую карточку линии
      gh workflow run build.yml --ref main 2>/dev/null || true
      gh workflow run conveyor.yml --ref main -f lane="${LANE:-both}" 2>/dev/null || true
      exit 0
    fi
    why="слияние не удалось: $(tail -c 200 /tmp/pm.txt)"
  fi
  tg "👀 $CARD: PR ждёт тебя — $why. $URL"
fi
gh workflow run build.yml --ref "$BR" 2>/dev/null || true
exit $ok
