---
name: "stalkerzone-dev"
description: "Работа над модом Stalker Zone (Forge 1.20.1): сборка, тесты, CI, проверка на сервере, перенос уровней S.T.A.L.K.E.R. 1:1, доставка сборки игроку."
---

# Stalker Zone: как устроена работа

Отвечать по-русски. Экономить лимит: крупные этапы одним заходом, одна проверка на сервере в конце этапа, превью-картинки только по просьбе, большие логи и списки папок не читать целиком (grep/tail).

## Начало и конец сессии (обязательно)
- **Первым делом** прочитать документ проекта `claude/00-start-here.md` (Projects → project_read): текущая цель, журнал решений, открытые проблемы, карта документов (что актуально, что архив).
- Прежде чем спросить пользователя о факте или решении — искать в `00-start-here.md` и актуальных документах (`project_search`). При противоречии верно более новое.
- **В конце сессии** (и после каждого крупного решения) обновить `claude/00-start-here.md`: «Сейчас», журнал решений (дата, новые сверху), «Открытые проблемы», дата в шапке. Новые решения по дизайну — в соответствующий актуальный документ.
- Документы с пометкой «АРХИВ» — история, не руководство.

## Репозиторий
- GitHub `Grilik/stalkerzone`, ветка `main`, **публичный**. Рабочая копия: `/home/claude/stalkerzone` (раньше `/home/claude/sz_repo`; если нет — `add_repo` Grilik/stalkerzone с access push, затем `git clone --depth 1`).
- Коммиты: `git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit`, в конце сообщения — строки атрибуции из системного напоминания (Co-Authored-By, Claude-Session).
- Изменения — через ветку и PR: REST API (`gh api repos/Grilik/stalkerzone/pulls -f title=... -f head=... -f base=main`), GraphQL (`gh pr create`) через прокси недоступен; слияние `gh api -X PUT repos/.../pulls/N/merge -f merge_method=squash`.
- После каждого этапа: обновить README.md (раздел версии) и CHECKLIST.md (пункты проверки в игре), коммит, push, проверить CI, записать итог в документ проекта (`claude/stalker-mod-*.md` через Projects) и в `claude/00-start-here.md`.
- Карточки задач: `docs/tasks/README.md` (таблица, статусы); обмеры построек Кордона: `docs/kordon_objects/`.
- **Нельзя коммитить данные игры GSC** (`*.szl`, извлечённые файлы): они в .gitignore.

## Конвейер (GitHub Actions)
- `conveyor.yml`: линии `world` и `sim`; `sim` без своих карточек берёт карточки мира с конца очереди; карточка захватывается веткой `task/NNN-auto`.
- Исполнитель Cline + `deepseek/deepseek-v4.1-flash` (V4 Pro игнорируется ради экономии), старший — Claude Code по подписке (до 5 вызовов в сутки, `@claude` в комментарии PR от владельца), ревьюер `z-ai/glm-5.3-flash` (`tools/review/ai_review.py`). Стоимость карточки и остаток OpenRouter — в описании PR.
- Ревьюер даёт ложные «БЛОКЕРЫ» (не видит код вне diff, PR без карточки): проверять по коду, а не по вердикту.
- Частые конфликты: параллельные карточки построек правят `Prefabs.java` (список сборщиков), `PrefabsTest`, `WorkRolesTest`, таблицу статусов. Разрешать объединением; проверять, что прямоугольники CLEAR сборщиков не пересекаются и что обмер не захватил соседний объект (так «башня элеватора» встала на портал туннеля).

## Сборка (облако)
- JDK 17: `/opt/jdk17` (если нет — скачать Temurin 17 с GitHub releases, распаковать). В `~/.gradle/gradle.properties`: `org.gradle.java.installations.paths=/opt/jdk17` и `org.gradle.java.installations.auto-download=false`.
- `JAVA_HOME=/opt/jdk17 ./gradlew build --no-daemon -q --max-workers=1 2>&1 | grep -v "JAVA_TOOL\|^Note\|warning"` (~1 мин; при 429 от Maven повторить). Jar: `build/libs/stalkerzone-0.1.0.jar`.

## Тесты симуляции (main-программы, не JUnit)
```
rm -rf /tmp/claude-0/simout && mkdir -p /tmp/claude-0/simout && javac --release 17 -encoding UTF-8 -d /tmp/claude-0/simout $(find src/main/java/ru/stalkerzone/sim src/main/java/ru/stalkerzone/zonemap -name '*.java') src/main/java/ru/stalkerzone/world/ZoneBounds.java src/main/java/ru/stalkerzone/artifact/ArtifactDef.java src/test/java/*.java
for f in src/test/java/*Test.java; do t=$(basename $f .java); timeout 600 java -cp /tmp/claude-0/simout $t >/tmp/claude-0/t_$t.log 2>&1 && echo -n "OK $t; " || echo "FAIL $t"; done
```
При падении смотреть `grep FAIL` в логе (вывод с `-Dstdout.encoding=UTF-8`). Новые чистые классы, нужные тестам, добавлять в список компиляции и в `.github/workflows/build.yml`.

## CI
Ждать: `until [ "$(gh run list -R Grilik/stalkerzone --limit 1 --json headSha,status --jq '.[0].headSha[0:7]+" "+.[0].status')" = "<sha7> completed" ]; do sleep 15; done`, затем `--json conclusion` (или `gh api repos/Grilik/stalkerzone/actions/runs?branch=...`). Логи CI через прокси недоступны — смотреть аннотации (`check-runs/<job>/annotations`). Тесты CI **не ловят** ошибки данных мира (JSON worldgen) и вид построек — для этого сервер.

## Проверка на сервере (облако)
- `run/server.properties`: `level-type=stalkerzone\:zone` (иначе мир обычный!), `enable-rcon=true`, `rcon.password=sz`, `rcon.port=25575`, `max-tick-time=-1`, `online-mode=false`, `level-name=<новое имя>`; `run/eula.txt` = eula=true.
- Уровни пользователя (`*.szl`, `layout.json`) брать с его ПК (папка игры `stalkerzone\levels`) в `run/stalkerzone/levels/`.
- Запуск: `(timeout 1500 ./gradlew runServer --no-daemon > /tmp/claude-0/srv.log 2>&1 &)`, ждать `until grep -q "Done (\|Crash" ...`. Ошибки реестра мира: `grep "Unbound values\|Failed to load registries"`.
- RCON-клиент `/tmp/claude-0/rcon.py` (класс R, метод cmd; пакет: тип 3 — логин, тип 2 — команда). Проверка блоков: `execute if block x y z minecraft:...`. Чанки: `forceload add` небольшими областями (большие — таймаут). Остановка: `stop`. Не делать `pkill -f` по строке, которая есть в собственной команде.
- Игрока нет: ванильные боты не пускаются (нужен Forge-клиент). Для логики «игрок рядом» — локальный (не коммитить!) класс с `FakePlayerFactory.get` + `level.addNewPlayer` + `teleportTo`.
- Зависание при загрузке чанка = jstack серверного потока (`/opt/jdk17/bin/jstack <pid> | grep -A25 '"Server thread"'`). Признак у пользователя: в логе нет записей сервера после входа и нет «Saving worlds» при выходе.

## Правила кода, найденные на граблях
- Всё, что ставит блоки при `ChunkEvent.Load`, — внутри `ObjPropBlock.worldgen(...)` и только через `ObjPropBlock.safeSet` / `placeInChunk`: иначе onPlace/onRemove, блоки с BlockEntity и жидкости лезут в соседний неготовый чанк и вешают сервер.
- Код «при подходе игрока» (декор, лагеря) не должен читать блоки/высоту за пределами проверенных `hasChunk` чанков — это синхронная генерация.
- ID ванильных placed feature без ульев: `minecraft:oak_checked`, `fancy_oak_checked`, `birch_checked` (`minecraft:birch` и `fancy_oak` не существуют → мир не создаётся).
- Enum-ы, сохраняемые по номеру (AnomalyType, LairKind, MutantType, ArtifactDef), — только дописывать в конец.
- Смена набора узлов/фракций симуляции — поднять версию сохранения в `Simulation.save/load`.
- Закон сохранения: любые товары/деньги между миром и симуляцией — через ledger (imported/exported/moneyMinted/moneyBurned), проверка `sim.audit()`.

## Уровни оригинала 1:1 (tools/xray)
- Игра пользователя: `C:\Program Files (x86)\Steam\steamapps\common\STALKER Shadow of Chornobyl - EE` (архивы `levels\levels.db0..3`, `resources\*.db`, формат X-Ray 2947, файлы внутри не сжаты). Anomaly: `C:\MineStalker\Anomaly` (Кордон импортирован из Anomaly).
- Через мост нельзя передать файл >400 МБ, поэтому нужные куски вырезает на ПК пользователя `C:\MineStalker\stalker_tools\cut.bat` по списку `cut_list.txt` (строки `архив|имя|смещение|длина`) в `C:\MineStalker\stalker_dump\cut`. Смещения брать из оглавлений: `C:\MineStalker\stalker_dump\*.table` → `tools/xray/xdb <table> list` (колонки: размер, сжатый, смещение, имя). Затем `device_stage_files`.
- `cd tools/xray && make` (скачает LzHuf.cpp из OpenXRay). Шаги: `rast level.cform esc.bin 1.0` → `vox level.cform vox.bin` → `python3 -I export.py <id>.szl` (сдвиг уровня в начале export.py; MC x = xr_x+DX, MC z = -xr_z+DZ, y = xr_y+100).
- Материалы (gamemtl.xr, id): 2 wood, 3 concrete, 4 asphalt, 5 metal, 6 glass, 7 earth, 8 gravel, 10 dirt, 11 water, 12 tile, 13 metal_plate, 15 grass, 16 sand, 18 bricks, 19 wooden_board, 34 pipe, 37 bush, 39 tree_trunk, 48 rabica, 49 shifer, 53 stucco, 60 tin, 80 fake (невидимые стены — пропускать), 81 cloth, 82 ladders. Блоки — `XrayPlacer.material`.
- Глобальная карта: `config/game_maps_single.ltx` (global_rect ≈ 3,35 м/пиксель). Кордон: деревня новичков = (0,0) мира. Узлы симуляции в `ZoneScenario` в единицах ×10 блоков; узлы Кордона и их точки — `src/main/resources/data/stalkerzone/points/l01_escape.json`.
- `.szl` кладётся рядом с игрой: `<папка игры>/stalkerzone/levels/`, в облаке — `run/stalkerzone/levels/`.

## Доставка игроку
- Основной путь — релиз `nightly` (собирается CI из каждого коммита в main): https://github.com/Grilik/stalkerzone/releases/download/nightly/stalkerzone-0.1.0.jar. Проверить, что `target_commitish` релиза = текущий main. После изменений построек/симуляции — новый мир.
- Мост: `device_commit_files` (≤30 МБ на файл) в `C:\MineStalker\...`; `.bat` с `chcp 65001`, `.ps1` в UTF-8 с BOM и CRLF. Скриншоты плейтеста: `C:\MineStalker\PlayTest\<Локация>\<Постройка>`.
- Игрок запускает Legacy Launcher, версия «Forge 1.20.1» (Forge 47.4.28), папка игры: `%APPDATA%\.tlauncher\legacy\Minecraft\game\home\Forge 1.20.1` (mods, stalkerzone\levels, logs\latest.log). Сборка модов ставится `C:\MineStalker\stalker_tools\modpack.bat`; старые `stalkerzone-*.jar` убирать (две версии мода — не запустится).
- Если лаунчер висит на «Загрузка версий» / «Проверка ресурсов» с `Malformed reply from SOCKS server` в `.tlauncher\logs\launcher.log` — это VPN/прокси Windows, не моды.