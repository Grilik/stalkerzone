# 063. Налёт от перенаселения: ничьё логово нападает, только когда стая переполнена

**Конвейер:** да
**Тест:** `docs/tasks/tests/LairOverpopulationTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/Simulation.java`
**Чек-лист:** нет

Решение пользователя 06.11: когда мутантов становится слишком много, они атакуют заставы и базы.

## Причина (найдена «Программистом»)
`Simulation.lairsDaily`: ничьё логово идёт в налёт, как только сила > `lairRaidMin` (25 у Кордона), — при неполной стае.

## Что сделать
В условии налёта ничьего логова добавить `l.mutantCount() >= Lair.MAX_MUTANTS` (остальное как было: сила > `lairRaidMin`,
`raidCooldown == 0`). Цели налёта не менять.

## Готово, когда
`LairOverpopulationTest` зелёный, `bash tools/conveyor/check.sh` зелёный (`KordonLairsTest`, `KordonContractsTest` уже
подготовлены к меньшему числу налётов); статус 063 в `docs/tasks/README.md` — «готово (конвейер)».
