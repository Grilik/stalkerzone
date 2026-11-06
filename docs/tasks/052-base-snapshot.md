# 052. Данные пульта базы: обзор, жители, постройки, склад, журнал (BaseSnapshot)

**Конвейер:** да
**Тест:** `docs/tasks/tests/BaseSnapshotTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/BaseSnapshot.java`
**Чек-лист:** нет

Данные для П-09 (пульт базы как ратуша MineColonies, `docs/claude/04-interfaces.md`, раздел 3). Экран — не здесь.

## Причина (найдена «Программистом»)
Заготовка `BaseSnapshot.build(sim, node)` возвращает пустую строку.

## Что сделать
Собрать строки по формату из шапки теста: `O` (узел, название, владелец, `NodePopulation.number`, `housing`, касса
группировки, `phase.name()`), `R` на каждого из `s.people.of(владелец)` (имя, `role.ru`, `cls.ru`, чем занят: «ранен,
медпункт» при `injury > 0`, иначе «на базе»; здоровье `clamp(100 − injury × 25)`), `B` на каждую из `s.buildingsAt(node)`
(`type.name()`, `level`, `Building.MAX_LEVEL`), `S` на каждый `Good.ALL` (`name()`, `stock.get`), `J` — до 10 последних
`s.events`, где есть название узла или группировки. Ничей узел — пустая строка. Табуляции и переводы строк в тексте чистить.

## Готово, когда
`BaseSnapshotTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 052 в `docs/tasks/README.md` — «готово (конвейер)».
