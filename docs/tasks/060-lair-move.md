# 060. Перенос логов: «Собачий лес» (21), «Лысый холм» (13), без тушканов в туннеле

**Конвейер:** да
**Тест:** `docs/tasks/tests/LairMoveTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/KordonScenario.java`, `src/main/java/ru/stalkerzone/sim/KordonBalance.java`, `src/main/java/ru/stalkerzone/sim/Simulation.java`, `src/main/java/ru/stalkerzone/zonemap/build/Prefabs.java`
**Чек-лист:** нет

Решение пользователя 06.11 (22:50–23:20), схема территорий — `docs/claude/10-log-programmer.md`, запись 06.11 (23:40).

## Причина (найдена «Программистом»)
Логово Л3 стояло у свалки (−92, −100), «Холм за элеватором» — у АТП (там будут бандиты), тушканы — у туннеля,
где теперь две заставы одиночек.

## Что сделать
1. `KordonScenario`: узел 13 — `"Лысый холм"`, (20, 5), `den = {200, 45}`; дорога `HILL–ELEVATOR` → `HILL–WAGON` (1)
   и `HILL–SOUTH_POST` (2). Узел 21 — `"Собачий лес"`, (5, −17), `den = {45, −175}`; к дороге `DUMP–DUMP_FOREST`
   добавить `VILLAGE–DUMP_FOREST`, `WAGON–DUMP_FOREST`, `DUMP_FOREST–TUNNEL_SOUTH` (по 1 дню). `COORDS` — те же точки.
   `TUNNEL` убрать из `LAIR_SITES`.
2. `KordonBalance`: убрать `LairDef(7, RODENTS, …)` из `START_LAIRS`; `ALL_DISTRICTS = {DISTRICT_SOUTH, DISTRICT_NORTH}`,
   `ALL_POOLS = {POOL_SOUTH, POOL_NORTH}` (массивы параллельны — убирать оба).
3. `Prefabs.LAIR_L3 = {45, -175}`.
4. `Simulation`: версия сохранения 21, Кордон версии < 21 — новый мир с тем же сидом.

## Готово, когда
`LairMoveTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 060 в `docs/tasks/README.md` — «готово (конвейер)».
