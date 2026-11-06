# 064. Грызня видов: разные виды дерутся без людей рядом, туша — в логово убийцы

**Конвейер:** да
**Тест:** `docs/tasks/tests/MutantRivalryTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/LairTerritory.java`, `src/main/java/ru/stalkerzone/sim/Simulation.java`
**Чек-лист:** нет

Решение пользователя 06.11: мутанты могут атаковать мутантов другого вида, если людей поблизости нет. Запускать после 062.
В мире правило применит карточка 065 (не конвейер).

## Что сделать
1. `LairTerritory.hostile(LairKind a, LairKind b, boolean humansNear)` — `a != b && !humansNear`.
2. `Simulation.mutantKilledByMutant(Lair victim, Lair killer, double value)`: если `killer` ещё в `lairs` —
   `killer.feed(value * 0.5)`; затем `return mutantKilled(victim, value, false)`.

## Готово, когда
`MutantRivalryTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 064 в `docs/tasks/README.md` — «готово (конвейер)».
