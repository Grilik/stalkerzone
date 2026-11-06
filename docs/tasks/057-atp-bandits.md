# 057. Бандиты только на АТП: база бандитов, гарнизон по уровню, без отрядов и захвата

**Конвейер:** да
**Тест:** `docs/tasks/tests/AtpBanditsTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/KordonScenario.java`, `src/main/java/ru/stalkerzone/sim/NodePopulation.java`
**Чек-лист:** нет

Решение пользователя 06.11, 23:50: бандиты возвращаются — АТП их база (не брошенная), гарнизон по уровню базы,
враждебны игроку и одиночкам (058), без отрядов и захвата точек. Запускать **после 054** (`Faction.garrisonOnly`, `create(seed, war)`).

## Что сделать
1. `KordonScenario.create(seed, war)`: `BANDITS.garrisonOnly = !war`; дальний блокпост (`farPost.owner = BANDITS`) — только при `war`;
   АТП у бандитов в обоих сценариях.
2. `NodePopulation`: `public static int garrisonByLevel(int level)` = `min(BASE_CAP, 4 + 2 × level)`; в `number` для фракции
   с `garrisonOnly` — не больше `garrisonByLevel` максимального уровня построек узла (`s.buildingsAt`).

## Готово, когда
`AtpBanditsTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 057 в `docs/tasks/README.md` — «готово (конвейер)».
