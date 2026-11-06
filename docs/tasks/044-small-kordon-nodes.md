# 044. Узлы «Малого Кордона»: 19 О7, 20 А6/О12, 21 Л3; версия сохранения 19

**Конвейер:** да
**Тест:** `docs/tasks/tests/KordonSmallNodesTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/KordonScenario.java`, `src/main/java/ru/stalkerzone/sim/KordonBalance.java`, `src/main/java/ru/stalkerzone/sim/Simulation.java`
**Чек-лист:** нет

П-17, план `docs/claude/07-plan-to-bridge.md`, модель — `docs/claude/01-malyi-kordon.md` («Симуляция участка»).
Порядок запуска: **044 → 059 → 045 → остальные**.

## Причина (найдена «Программистом»)
В `KordonScenario.create` 19 узлов (0–18); лагеря у вагончика, свалки техники и логова Л3 в симуляции нет — площадкам
и NPC не к чему привязаться. На номера узлов опираются сохранения, `KordonBalance` и контракты, поэтому новые узлы —
только в конец списка, а массивы по числу узлов (`KordonBalance.RAID_TARGET_WEIGHT = new double[19]`) надо расширить.

## Что сделать
1. В `KordonScenario`: константы `WAGON = 19, DUMP = 20, DUMP_FOREST = 21` (public); после узла `edge` добавить
   `node(nodes, "Лагерь у вагончика", NodeType.OUTPOST, 12, -7)` (housing 8, defense 20),
   `node(nodes, "Свалка техники", NodeType.ANOMALY, -4, -12)`, `node(nodes, "Лес у свалки техники", NodeType.RUINS, -9, -10)` (логово Л3 — к западу от площадки свалки, см. 047);
   дороги до `g.build()`: `VILLAGE–WAGON` (1 день), `VILLAGE–DUMP` (2), `DUMP–DUMP_FOREST` (1); владельцы
   `wagon.owner = dump.owner = ZoneScenario.LONERS` (застава О12 стоит на краю свалки с начала, Л3 ничей);
   `DUMP_FOREST` — в конец `LAIR_SITES`; три новые строки в конец `COORDS`.
2. `KordonBalance.RAID_TARGET_WEIGHT` — размер 22.
3. `Simulation.save`: `out.writeInt(19)`; `load`: допустимая версия до 19, и для Кордона версии < 19 —
   `return Scenarios.create(scenario, seed)` (как сейчас для < 14): старый мир Кордона начинается заново с тем же сидом.

## Готово, когда
`KordonSmallNodesTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 044 в `docs/tasks/README.md` —
«готово (конвейер)».
