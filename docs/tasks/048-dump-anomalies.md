# 048. А6: поле аномалий в чаше свалки техники, радиация, артефакты после выброса

**Конвейер:** да
**Тест:** `docs/tasks/tests/DumpAnomaliesTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/KordonScenario.java`
**Чек-лист:** нет

Модель — `docs/claude/01-malyi-kordon.md`, раздел 4. Запускать после 044.

## Причина (найдена «Программистом»)
У узла 20 нет района аномалий (`sim.anomalyDistricts`) и радиации (`sim.radiationNodes`). Переезд аномалий внутри поля
и созревание артефактов после выброса уже работают для любого района (`Simulation.emission`, карточки 013, 029).

## Что сделать
В `KordonScenario.create` рядом с остальными районами:
`new AnomalyDistrict(DUMP, "Свалка техники А6", 6, 9, 1.0, 20, AnomalyType.MOSQUITO, AnomalyType.SPRINGBOARD)`,
`setCenter(5, 0)` (центр чаши (−35, −120) = узел (−40, −120) + (5, 0)), добавить в `sim.anomalyDistricts`;
`DUMP` — в `sim.radiationNodes`.

## Готово, когда
`DumpAnomaliesTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 048 в `docs/tasks/README.md` — «готово (конвейер)».
