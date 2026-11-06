# 055. Огонь на поражение в запретной полосе перед южным блокпостом

**Конвейер:** да
**Тест:** `docs/tasks/tests/KillZoneTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/zonemap/KillZone.java`, `src/main/java/ru/stalkerzone/zonemap/build/Prefabs.java`, `src/main/java/ru/stalkerzone/npc/StalkerEntity.java`
**Чек-лист:** `docs/checklist/055-kill-zone.md`

Решение пользователя 06.11: военные стреляют на поражение, если игрок или одиночка входит в запретную полосу перед
периметром; за полосой не трогают. Полосы — метки `kill_line` сборщика `KordonSouthPost` (П-20, PR #99): по 4 угла
на прямоугольник (полоса перед северным забором и перед проездом на дороге).

## Причина (найдена «Программистом»)
Заготовка `zonemap/KillZone` возвращает пустой список / false; метки `kill_line` никто не читает.

## Что сделать
1. `KillZone.fromPoints`: каждые 4 точки подряд → `{min x, min z, max x, max z}`; неполная четвёрка не считается.
   `inside` — точка в одном из прямоугольников (края включительно); `militaryHostile` = `inside`.
2. `Prefabs.killZones(levelId, g, lvl)`: метки `kill_line` всех площадок уровня (по `sites(levelId)` и
   `points(levelId, g, lvl, site.node)`) по порядку → `KillZone.fromPoints`.
3. Мир (`StalkerEntity.hostileTo` и `isEnemyNpc`): NPC фракции с `garrisonOnly` (военные блокпоста, 054) — игрок и
   одиночка враги, только если стоят в полосе (`KillZone.militaryHostile`); полосы считать один раз на уровень
   (земля — `LevelRegions.byId(levelId)::groundBlock`, как в `NpcManager.prefabPoints`). Пока 054 не слита — без условия
   на фракцию для военных.
4. Чек-лист `docs/checklist/055-kill-zone.md`: подойти к северному забору блокпоста — огонь; отойти дальше — не стреляют.

## Готово, когда
`KillZoneTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 055 в `docs/tasks/README.md` — «готово (конвейер)».
