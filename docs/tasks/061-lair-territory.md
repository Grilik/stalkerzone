# 061. Территория логова: круг по виду + вытянутая часть, минус зоны людей (LairTerritory)

**Конвейер:** да
**Тест:** `docs/tasks/tests/LairTerritoryTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/LairTerritory.java`, `src/main/java/ru/stalkerzone/sim/KordonScenario.java`
**Чек-лист:** нет

Решение пользователя 06.11: у логова обширная зона-пустырь, где бродят его мутанты. Запускать после 060.

## Причина (найдена «Программистом»)
Территории логова нет: мутанты держатся радиусом вокруг логова и заходят в застройку. Заготовка `LairTerritory`
(возвращает 0 / false) и поля `Node.reach`, `Node.reachWidth` уже есть (PR П-17б).

## Что сделать
1. `LairTerritory.radius`: DOGS 75, FLESH 60, прочие 50.
2. `humanZone(s, x, z, except)`: точка ближе радиуса зоны людей к центру любого **занятого** узла (кроме `except`):
   BASE 70, SETTLEMENT 50, прочие 30 блоков (центр узла = `x × 10, z × 10`).
3. `contains(s, l, x, z)`: не зона людей (кроме узла самого логова) и (в радиусе от `Lair.denXZ` или ближе
   `reachWidth` к ломаной `node.reach`).
4. `KordonScenario`: Дыре в заборе (узел 9) `reach = {{433, −295}, {410, −225}, {340, −200}, {270, −172}}`, `reachWidth = 34`
   (к старому месту холма, мимо АТП и элеватора; с Лысым холмом не соединяется).

## Готово, когда
`LairTerritoryTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 061 в `docs/tasks/README.md` — «готово (конвейер)».
