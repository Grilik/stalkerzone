# 062. Логово — база стаи: добыча (Lair.feed), убитые налётом — в логово

**Конвейер:** да
**Тест:** `docs/tasks/tests/LairPreyTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/Lair.java`, `src/main/java/ru/stalkerzone/sim/KordonBalance.java`, `src/main/java/ru/stalkerzone/sim/Simulation.java`
**Чек-лист:** нет

Решение пользователя 06.11: в логово стая стаскивает добычу и размножается. Размножение уже есть: `mutantCount` растёт с силой.

## Что сделать
1. `Lair`: поле `public double preyEaten` (не сохраняется) и `public double feed(double food)` — сила растёт на
   `min(food, cap() − strength)`, съеденное прибавить к `preyEaten` и вернуть; `food ≤ 0` — 0.
2. `KordonBalance.PREY_PER_PERSON = 2.0`.
3. `Simulation.raid`: после потерь логова — `l.feed(dead * KordonBalance.PREY_PER_PERSON)`.

## Готово, когда
`LairPreyTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 062 в `docs/tasks/README.md` — «готово (конвейер)».
