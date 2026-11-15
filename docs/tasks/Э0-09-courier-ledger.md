# Деньги курьера — через Ledger

**Конвейер:** да
**Тест:** `docs/tasks/tests/CourierLedgerTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/Simulation.java`, `src/main/java/ru/stalkerzone/npc/CaravanManager.java`
**Чек-лист:** нет

Задача Э0-09 дорожной карты (`docs/roadmap.md`, `docs/prompts/Э0-09.md`). Запускать вручную: Actions → conveyor, `card` = `Э0-09`.
Видно в игре: гибель курьера Торговцев (`CaravanManager.caravanNpcKilled`) — груз пропадает, аудит симуляции не ломается.

## Ошибка
`CaravanManager.caravanNpcKilled` (≈ строки 171–178) при гибели курьера сам пишет в `sim.ledger.destroyed`, а деньги обнуляет (`cv.cargo.money = 0`) **без** `ledger.moneyBurned` — деньги исчезают мимо закона сохранения, `sim.audit()` не сходится.

## Что сделать
1. `Simulation.courierLost(Caravan c)` (public): весь груз курьера — в `ledger.destroyed` (как `Logistics`, ≈ строки 617–619: `ledger.destroyed[g] += c.cargo.take(g, c.cargo.get(g))`), деньги — в `ledger.moneyBurned`, `c.cargo.money = 0`. Повторный вызов ничего не списывает (груз уже пуст).
2. `CaravanManager.caravanNpcKilled`: вместо цикла по `sim.ledger.destroyed` и `cv.cargo.money = 0` — вызов `sim.courierLost(cv)`. Остальное (`resent`, `guards`, список) не менять.
3. Других записей `ledger.` вне `src/main/java/ru/stalkerzone/sim/` не должно остаться.

## Готово, когда
`CourierLedgerTest` зелёный, `bash tools/conveyor/check.sh` зелёный, `grep -rn "ledger\." src/main/java | grep -v "/sim/"` пуст.
