#!/usr/bin/env bash
# Конвейер карточек: исполнитель (дешёвая модель) -> проверка -> старший (сильная модель) при провале -> ревью -> PR.
# Нужны: OPENROUTER_API_KEY, GH_TOKEN. Модели: AGENT_MODEL, STRONG_MODEL. Карточка: CARD (или выбирается сама).
set -u
cd "$(git rev-parse --show-toplevel)"
AGENT_MODEL=${AGENT_MODEL:-deepseek/deepseek-v4-pro}
STRONG_MODEL=${STRONG_MODEL:-anthropic/claude-sonnet-5.5}
CARD=${CARD:-$(python3 -I tools/conveyor/next_card.py)}
[ -z "$CARD" ] && { echo "Нет карточки, готовой к выполнению."; exit 0; }
FILE=$(ls docs/tasks/${CARD}-*.md | head -1)
TITLE=$(head -1 "$FILE" | sed 's/^# *//')
BR="task/${CARD}-auto"
echo "Карточка $CARD: $TITLE"
git checkout -q -b "$BR"
git config user.name "stalkerzone-conveyor"; git config user.email "conveyor@users.noreply.github.com"
cline auth -p openrouter -k "$OPENROUTER_API_KEY" -m "$AGENT_MODEL" >/dev/null
RULES='Ты работаешь в конвейере без человека. НЕ делай git commit, push и PR — это делает конвейер.
Проверка: `bash tools/conveyor/check.sh` (тесты симуляции и сборка; печатает итог и хвост ошибок). Не читай большие логи целиком.
Simulation.java и другие большие файлы читай кусками (поиск, затем диапазон строк).
Если одна и та же ошибка не уходит после 3 попыток исправления — ОСТАНОВИСЬ: опиши проблему в файле ESCALATE.md
(что делал, текст ошибки, гипотезы) и заверши работу. Дальше задачу подхватит старший разработчик.
Временные файлы клади в /tmp, не в репозиторий. В конце поставь карточке статус «готово (конвейер)» в docs/tasks/README.md.'
run() { # модель, таймаут, файл с заданием
  echo "::group::cline $1"; cat "$3" | cline -y -m "$1" -t "$2" --retries 3 "Выполни задание из входных данных." >/tmp/cline-$$.log 2>&1
  tail -30 /tmp/cline-$$.log; echo "::endgroup::"
}
PHASES="исполнитель ($AGENT_MODEL)"
{ echo "Выполни карточку $FILE. Сначала прочитай её и .clinerules (правила про git/PR выше в этом задании важнее)."; echo; echo "$RULES"; } > /tmp/pA.txt
run "$AGENT_MODEL" 3600 /tmp/pA.txt
bash tools/conveyor/check.sh > /tmp/check1.txt 2>&1; ok=$?
if [ $ok -ne 0 ]; then
  PHASES="$PHASES → старший ($STRONG_MODEL)"
  { echo "Ты старший разработчик. Младший агент выполнял карточку $FILE и не справился. Не переделывай с нуля — найди причину и исправь минимально.";
    echo; echo "$RULES"; echo; echo "## ESCALATE.md от младшего"; cat ESCALATE.md 2>/dev/null || echo "(нет)";
    echo; echo "## Изменённые файлы"; git status --short | head -50; echo; echo "## Результат проверки"; tail -80 /tmp/check1.txt; } > /tmp/pB.txt
  run "$STRONG_MODEL" 1800 /tmp/pB.txt
  bash tools/conveyor/check.sh > /tmp/check1.txt 2>&1; ok=$?
fi
rm -f ESCALATE.md
git add -A -- src docs tools .github .gitignore 2>/dev/null
git commit -q -m "$CARD: $TITLE (конвейер: $PHASES)" || { echo "Агент ничего не изменил."; exit 1; }
REVIEW=""
if [ $ok -eq 0 ]; then
  git push -q -u origin "$BR"
  BASE_SHA=$(git merge-base HEAD origin/main) HEAD_REF="$BR" python3 -I tools/review/ai_review.py > /tmp/review.md
  if ! grep -qE "Вердикт:\*\*\s*ОК" /tmp/review.md || grep -q "### Блокеры" /tmp/review.md; then
    PHASES="$PHASES → старший по ревью"
    { echo "Ты старший разработчик. По карточке $FILE работа сделана, тесты зелёные, но ревьюер нашёл замечания.";
      echo "Исправь обоснованные замечания минимально; необоснованные — пропусти и кратко объясни почему в файле REVIEW_REPLY.md."; echo; echo "$RULES"; echo; cat /tmp/review.md; } > /tmp/pC.txt
    run "$STRONG_MODEL" 1200 /tmp/pC.txt
    bash tools/conveyor/check.sh > /tmp/check1.txt 2>&1; ok=$?
    REPLY=$(cat REVIEW_REPLY.md 2>/dev/null); rm -f REVIEW_REPLY.md
    git add -A -- src docs tools .github .gitignore; git commit -q -m "$CARD: правки по ревью" || true
    BASE_SHA=$(git merge-base HEAD origin/main) HEAD_REF="$BR" python3 -I tools/review/ai_review.py > /tmp/review.md
    [ -n "$REPLY" ] && printf '\n\n### Ответ старшего на ревью\n%s\n' "$REPLY" >> /tmp/review.md
  fi
fi
git push -q -u origin "$BR"
{ echo "Карточка \`$FILE\` выполнена конвейером."; echo; echo "**Этапы:** $PHASES"; echo; echo "**Проверка (тесты симуляции + сборка):**";
  echo '```'; tail -40 /tmp/check1.txt; echo '```';
  [ $ok -ne 0 ] && echo && echo "⚠️ Проверка не прошла даже после старшего — нужен человек (@Grilik): перешлите ссылку архитектору.";
  echo; echo "🤖 Generated with [Claude Code](https://claude.com/claude-code)"; } > /tmp/body.md
if [ $ok -eq 0 ]; then
  URL=$(gh pr create --head "$BR" --base main --title "$CARD: $TITLE" --body-file /tmp/body.md)
else
  gh label create needs-human --color B60205 --description "Конвейер не справился" 2>/dev/null || true
  URL=$(gh pr create --head "$BR" --base main --draft --label needs-human --title "$CARD: $TITLE — нужен человек" --body-file /tmp/body.md)
fi
echo "PR: $URL"
[ -f /tmp/review.md ] && gh pr comment "$URL" --body-file /tmp/review.md
gh workflow run build.yml --ref "$BR" 2>/dev/null || true
exit $ok
