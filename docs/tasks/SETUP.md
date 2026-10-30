# Как подключить ИИ-команду к разработке (Windows)

## Кто что делает
| Роль | Кто | Что делает |
|---|---|---|
| Заказчик и тестер | вы | запускаете карточки в Cline, сливаете PR, играете и пишете, что не так |
| Исполнитель | Cline + модель через OpenRouter | выполняет **одну** карточку из `docs/tasks/` = один PR |
| Архитектор | Claude (этот чат) | пишет карточки, импортирует уровни из игры (карточка 004), ревью на вехах, сложные баги |

Правила для исполнителя лежат в `.clinerules` в корне репозитория — Cline читает их сам, вставлять в чат не нужно.

## 1. Программы (один раз)
1. **Git for Windows** — https://git-scm.com/download/win, всё по умолчанию.
2. **JDK 17** (Temurin) — https://adoptium.net, версия 17, при установке включить «Set JAVA_HOME».
   Проверка в PowerShell: `java -version` → `17.x`.
3. **GitHub CLI** — https://cli.github.com, затем в PowerShell: `gh auth login` → GitHub.com → HTTPS → браузер.
4. **VS Code** — https://code.visualstudio.com.
5. Python и WSL **не нужны**: уровни из игры импортирует Claude.

## 2. Репозиторий
```powershell
cd C:\MineStalker
git clone https://github.com/Grilik/stalkerzone sz
cd sz
git config user.name "Grilik"
git config user.email "ваша почта на GitHub"
```
Старую папку `C:\MineStalker\stalkerzone` больше не используйте для сборки — работаем только в `C:\MineStalker\sz`.
В VS Code: File → Open Folder → `C:\MineStalker\sz`.

Проверка, что всё собирается (до первой карточки):
```powershell
powershell -ExecutionPolicy Bypass -File tools/simtest.ps1
./gradlew.bat build
```
Оба шага должны закончиться без FAIL/ошибок. Если нет — пришлите Claude последние 30 строк вывода.

## 3. OpenRouter
1. https://openrouter.ai/keys → Create Key. **Задайте лимит ключа** (Credit limit), например $5:
   агент в цикле не потратит больше.
2. Скопируйте ключ (`sk-or-...`), он показывается один раз.

## 4. Cline
1. VS Code → Extensions (Ctrl+Shift+X) → `Cline` → Install. Слева появится значок Cline.
2. Шестерёнка Cline → **API Provider: OpenRouter**, вставить ключ.
3. **Модели** (включить «Use different models for Plan and Act»):
   - Act: `deepseek/deepseek-v4-pro` — основная рабочая лошадка, дешёвая и хорошо пишет Java.
   - Plan: та же модель. На тяжёлых карточках (помечены «модель: сильная») — `z-ai/glm-5.3`
     или `anthropic/claude-sonnet-5.5` только для Plan: план дорогой моделью, код дешёвой.
   - Точные имена проверяйте поиском в списке моделей Cline: версии меняются.
4. **Auto-approve** (галочки над полем ввода):
   - Read files ✔, Edit files ✔, Execute safe commands ✔;
   - Execute all commands ✘, Use browser ✘, Use MCP ✘;
   - Max requests: 40 (потом Cline спросит, продолжать ли — это защита от зацикливания).
5. Checkpoints оставьте включёнными: можно откатить правки агента одной кнопкой.

## 5. Цикл одной карточки
1. Выберите в `docs/tasks/README.md` первую карточку со статусом «к выполнению», у которой выполнены зависимости.
2. PowerShell в папке `sz`:
   ```powershell
   git checkout main; git pull
   git checkout -b task/005-war-balance
   ```
3. В Cline — **New Task**, режим **Plan**, текст:
   > Выполни docs/tasks/005-kordon-war-balance.md. Сначала прочитай карточку, .clinerules и код,
   > который она упоминает. Дай план по пунктам «Готово, когда».
4. Прочитайте план. Если агент собирается трогать то, что в разделе «Не делать», напишите ему об этом. Затем **Act**.
5. Когда агент закончит, проверьте сами:
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools/simtest.ps1
   ./gradlew.bat build
   ```
6. Попросите Cline: «Сделай коммит и PR по правилам .clinerules». Или сами: `git add -A; git commit -m "..."; git push -u origin HEAD; gh pr create --fill`.
7. На GitHub дождитесь зелёной галочки CI у PR → **Squash and merge**. В `docs/tasks/README.md` статус карточки поменяет агент в том же PR.
8. Следующая карточка — **новая задача** в Cline (не продолжайте старую: длинный контекст дорогой и тупит модель).

## 6. Когда агент буксует
- 3 неудачных попытки починить одну ошибку → Stop. Откатите чекпоинт, запустите карточку заново на сильной модели (п. 4.3).
- Не помогло → в этот чат Claude: номер PR (или ветки) и **последние 30–50 строк** ошибки. Не вставляйте логи целиком.
- Агент правит `build.gradle`, `gradle.properties`, `world/xray`, `tools/xray` без указания в карточке — отклоняйте.
- Карточки с «модель: Claude» делает архитектор сам — в Cline их не запускать.

## 7. Вехи — когда звать Claude
Ревью пачкой, а не каждого PR (экономия лимита Claude):
- **Веха 1** — после 001–003 и 005–007 (вся симуляция Кордона): «ревью вехи 1, PR #… – #…».
- **Веха 2** — после 004 и 008–011 (мир и старт игрока).
- **Веха 3** — после 012–015: Claude проверяет на сервере, собирает jar и ставит вам на ПК; затем ваш плейтест (016).
Claude проверяет CI, тесты и смоук-тест на сервере и пишет карточки-исправления, если что-то не так.

## 8. Частые проблемы
- `gh: command not found` — перезапустите VS Code после установки GitHub CLI.
- Gradle ругается на версию Java — `java -version` должна быть 17; иначе переустановите Temurin 17 с JAVA_HOME.
- Кракозябры в выводе PowerShell — `chcp 65001` перед командами.
- PowerShell не запускает .ps1 — добавляйте `-ExecutionPolicy Bypass`, как в командах выше.
- OpenRouter 402/лимит — пополните баланс или поднимите лимит ключа.
