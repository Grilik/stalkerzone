# 047. Логово Л3 «Лес у свалки техники»: слепые псы, логово у (−92, −100), днём у логова, ночью выходят

**Конвейер:** да
**Тест:** `docs/tasks/tests/LairL3Test.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/KordonBalance.java`, `src/main/java/ru/stalkerzone/sim/KordonScenario.java`, `src/main/java/ru/stalkerzone/sim/Lair.java`, `src/main/java/ru/stalkerzone/sim/Node.java`, `src/main/java/ru/stalkerzone/mutant/MutantEntity.java`
**Чек-лист:** нет

Запускать после 044.

## Причина (найдена «Программистом»)
1. В `KordonBalance.START_LAIRS` нет логова на узле 21.
2. `Lair.denXZ(Node)` ставит логово в 55 блоках от центра узла под углом от id — Л3 уехал бы от своего места. Место (−92, −100) — в лесу к западу от свалки: площадка `dump` (`KordonDump.CLEAR`, PR #86) занимает x −74..−3, поэтому кандидат модели (−72, −98) сдвинут на 20 блоков на запад.
3. Мутанты привязаны к логову радиусом 22 (`MutantEntity`: `restrictTo(den, 22)`) днём и ночью одинаково.

## Что сделать
1. `START_LAIRS`: `new LairDef(21, LairKind.DOGS, 20.0)` в конец.
2. `Node`: поле `public int[] den` (null — как раньше); `Lair.denXZ`: если `n.den != null` — вернуть его копию;
   `KordonScenario`: узлу 21 `den = {-92, -100}`.
3. `Lair`: `public int roamRadius(boolean night)` — днём 10, ночью 32; в `MutantEntity` вместо постоянного 22 —
   `roamRadius(level.isNight())` при привязке к логову (необязательно, тест проверяет только `Lair`).

## Готово, когда
`LairL3Test` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 047 в `docs/tasks/README.md` — «готово (конвейер)».
