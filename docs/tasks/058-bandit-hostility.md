# 058. Вражда бандитов АТП: маршруты одиночек обходят АТП, контракт «Разведка АТП»

**Конвейер:** да
**Тест:** `docs/tasks/tests/BanditHostilityTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/Simulation.java`, `src/main/java/ru/stalkerzone/sim/Contract.java`, `src/main/java/ru/stalkerzone/sim/ContractBoard.java`
**Чек-лист:** нет

Решение пользователя 06.11, 23:50: бандиты атакуют игрока и одиночек на базе и на подходе; маршруты одиночек обходят АТП.
Запускать **после 057**. Вражда в симуляции уже ≥ `Crossings.ENEMY` (тест сторожит); бой в мире идёт по ней сам.

## Что сделать
1. `Simulation.route`: прежнее тело — в `routeThroughCrossings`; новый `route` берёт его результат и, если путь проходит
   (не концами) через узел фракции с `garrisonOnly`, враждебной `f` (`hostilityTo ≥ Crossings.ENEMY`), берёт
   `pathAvoiding(from, to, маска таких узлов)`, если он есть. В `kordon_war` гарнизонов нет — маршруты прежние.
2. `Contract.Type`: `SCOUT("Разведка")` **в конец** (тип пишется ординалом); строка `describe` для SCOUT.
3. `ContractBoard.generateKordon`: пока АТП у бандитов — одиночки (`members ≥ CONTRACT_MIN_LONERS`, нет своего SCOUT на узел 5)
   с шансом 0,15 выставляют SCOUT через `issueCash` (450, 20 дней), `c.node = 5`. NPC его не берут (тип не в списке NPC).
4. `ContractBoard.scouted(s, id, player)`: SCOUT, TAKEN, тот же игрок → `payPlayer`, иначе null.

## Готово, когда
`BanditHostilityTest` зелёный, `bash tools/conveyor/check.sh` зелёный (`CrossingsTest` — маршруты войны не изменились);
статус 058 в `docs/tasks/README.md` — «готово (конвейер)».
