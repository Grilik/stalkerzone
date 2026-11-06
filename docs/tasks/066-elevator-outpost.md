# 066. Элеватор — малая застава одиночек: OUTPOST, 3–4 человека, без логова, укрытие

**Конвейер:** да
**Тест:** `docs/tasks/tests/ElevatorOutpostTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/KordonScenario.java`, `src/main/java/ru/stalkerzone/sim/NodePopulation.java`
**Чек-лист:** нет

Решение пользователя 06.11, 23:50 (в сообщении — «060»; номер 060 уже занят переносом логов). Запускать **после 054**.
Укрытие `shelter` и места заставы ставит «Строитель» в П-22 вместе с площадкой `elevator`; тест проверит укрытие, когда она появится.

## Что сделать
1. `KordonScenario.create(seed, war)`: элеватор — `war ? NodeType.RUINS : NodeType.OUTPOST`; без `war` — `owner = LONERS`;
   `sim.lairForbidden` без `war` — малые заставы и `ELEVATOR`.
2. `NodePopulation.number`: для узла 4 в сценарии «kordon» (не «kordon_war») — в пределах 3–4.

## Готово, когда
`ElevatorOutpostTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 066 в `docs/tasks/README.md` — «готово (конвейер)».
