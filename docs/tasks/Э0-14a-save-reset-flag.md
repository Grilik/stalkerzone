# Сброс сохранения: флаг и версия в Simulation

**Конвейер:** да
**Тест:** `docs/tasks/tests/SaveResetTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/Simulation.java`
**Чек-лист:** нет

Задача Э0-14a дорожной карты (`docs/prompts/Э0-14a.md`, решение D3). Запускать вручную **после слияния Э0-09**: Actions → conveyor, `card` = `Э0-14a`. Блокирует Э0-14b (сообщение игроку — ведущий).

## Сейчас
Версия сохранения — литералы `22` в `Simulation.save()` (`out.writeInt(22)`) и в `Simulation.load()` (`version > 22`). При несовместимой версии `load()` молча возвращает новую симуляцию (`return Scenarios.create(scenario, seed);` — несколько веток `if (version < …)`), и снаружи не видно, что был сброс.

## Что сделать
1. `public static final int SAVE_VERSION = 22;` — вместо обоих литералов. Номер версии не менять.
2. Поле `public int resetFromVersion = -1;` (не сохраняется). Во **всех** ветках сброса в `load()` возвращаемой симуляции ставится `resetFromVersion = version` (прочитанная версия сохранения).
3. Обычная загрузка своей версии — `resetFromVersion == -1`, состояние как раньше.

## Готово, когда
`SaveResetTest` зелёный, `bash tools/conveyor/check.sh` зелёный (`SimDeterminismTest` и остальные).
