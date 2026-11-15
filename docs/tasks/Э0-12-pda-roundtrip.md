# Снимок КПК: строки Q и U доходят до PdaModel

**Конвейер:** да
**Тест:** `docs/tasks/tests/PdaRoundTripTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/client/PdaModel.java`, `tools/ci/test_sources.sh`
**Чек-лист:** нет

Задача Э0-12 дорожной карты (`docs/prompts/Э0-12.md`, решение в2 — метки КПК на Xaero). Запускать вручную: Actions → conveyor, `card` = `Э0-12`.
Видно в игре: пока нет — только данные для меток отрядов и населения на карте КПК (Э3).

## Сейчас
`PdaSnapshot` (сервер) пишет строки `Q` (отряды в пути: владелец, что делают, людей, x, z, узел цели, маршрут id через `;`, шаг) и `U` (id узла, людей). `PdaModel.parse` (клиент) этих строк не знает и молча пропускает. `client/PdaModel.java` не входит в `tools/ci/test_sources.sh`, поэтому тесты его не компилируют.

## Что сделать
1. `PdaModel`: записи `SquadInfo(int owner, String what, int people, int x, int z, int target, String route, int step)` и `Population(int node, int people)`; списки `squads`, `population`; ветки `case "Q"` и `case "U"` в `parse` — поля по порядку строки.
2. `tools/ci/test_sources.sh`: добавить `src/main/java/ru/stalkerzone/client/PdaModel.java` (класс чистый, только `java.util`).
`PdaScreen` и `PdaSnapshot` не менять.

## Готово, когда
`PdaRoundTripTest` зелёный, `bash tools/conveyor/check.sh` зелёный.
