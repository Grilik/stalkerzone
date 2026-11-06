# 059. Аномальные зоны без уровней и их заставы: две заставы у входов туннеля (узлы 22, 23)

**Конвейер:** да
**Тест:** `docs/tasks/tests/ZoneOutpostsTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/KordonScenario.java`, `src/main/java/ru/stalkerzone/sim/KordonBalance.java`, `src/main/java/ru/stalkerzone/sim/Simulation.java`
**Чек-лист:** нет

Решение пользователя 06.11, 18:05: аномальная зона без уровней — детализированный объект; рядом с каждой зоной
застава одиночек; у туннеля с «электрами» (смарт-террейн 4_9, под ж/д насыпью) — 2 заставы, по одной у каждого входа.
Запускать **после 044** (узлы 19–21 уже есть).

## Причина (найдена «Программистом»)
Зоны А6 (узел 20) и туннель (узел 7) — тип `ANOMALY`; построек (а значит и уровня) у таких узлов симуляция не заводит
(`Simulation.seedBuildings`: для ANOMALY — `continue`), мир строит площадки на фиксированном `WORLDGEN_LEVEL` — это уже
так, тест это лишь сторожит. Не хватает застав у туннеля и таблицы «зона → заставы». У А6 застава О12 — сам узел 20 (044).

## Что сделать
1. `KordonScenario`: `TUNNEL_SOUTH = 22, TUNNEL_NORTH = 23`; в конец списка узлов (после 044)
   `"Застава у южного входа в туннель"` OUTPOST (13, −23) и `"Застава у северного входа в туннель"` OUTPOST (13, −29),
   housing 6, владелец `LONERS` (места уточнит обмер, `docs/kordon_objects/`); дороги `VILLAGE–TUNNEL_SOUTH` (2),
   `TUNNEL_SOUTH–TUNNEL` (1), `TUNNEL–TUNNEL_NORTH` (1), `TUNNEL_NORTH–FARM` (2); строки в `COORDS`;
   `public static final int[][] ZONE_OUTPOSTS = {{DUMP, DUMP}, {TUNNEL, TUNNEL_SOUTH, TUNNEL_NORTH}};`.
2. `KordonBalance.RAID_TARGET_WEIGHT` — размер 24.
3. `Simulation`: версия сохранения 20; Кордон версии < 20 — новый мир с тем же сидом.

## Готово, когда
`ZoneOutpostsTest` зелёный, `bash tools/conveyor/check.sh` зелёный (в т.ч. `KordonScenarioTest`: проходы через насыпь
считаются по всем дорогам туннеля); статус 059 в `docs/tasks/README.md` — «готово (конвейер)».
