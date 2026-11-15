# Копии тестов карточек совпадают с src/test/java

**Конвейер:** да
**Тест:** `docs/tasks/tests/ConveyorTest.java`
**Файлы:** `docs/tasks/tests/KordonSmallNodesTest.java`, `docs/tasks/tests/LairDenSiteTest.java`, `docs/tasks/tests/LairL3Test.java`, `docs/tasks/tests/ZoneOutpostsTest.java`
**Чек-лист:** нет

Задача Э0-13 дорожной карты (`docs/prompts/Э0-13.md`). Запускать вручную **после слияния Э0-12**: Actions → conveyor, `card` = `Э0-13`.
Видно в игре: нет — защита конвейера (он копирует тест карточки поверх `src/test/java`, устаревшая копия откатила бы тест).

## Сейчас
Четыре копии в `docs/tasks/tests/` устарели: `KordonSmallNodesTest`, `LairDenSiteTest`, `LairL3Test`, `ZoneOutpostsTest` отличаются от одноимённых файлов в `src/test/java/` (актуальны — в `src/test/java/`). `ConveyorTest` этого не проверяет.

## Что сделать
Обновить четыре копии: содержимое каждого `docs/tasks/tests/X.java` = `src/test/java/X.java`. Файлы в `src/test/java/` не менять. Новая проверка в `ConveyorTest` (копия теста = `src/test/java`, концы строк не важны) уже в тесте карточки.

## Готово, когда
`ConveyorTest` зелёный; `diff` каждой пары пуст; `bash tools/conveyor/check.sh` зелёный.
