# 056. Пулемётчик южного блокпоста: роль MG на месте «mg» (уровень 2+), ПКМ

**Конвейер:** да
**Тест:** `docs/tasks/tests/MachineGunnerTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/BaseRoles.java`, `src/main/java/ru/stalkerzone/npc/NpcManager.java`, `src/main/java/ru/stalkerzone/npc/StalkerEntity.java`
**Чек-лист:** `docs/checklist/056-machine-gunner.md`

Решение пользователя 06.11: на 2-м уровне блокпоста — пулемётная огневая точка, как в оригинале. Место `mg` сборщик
ставит только с уровня 2 (П-20, PR #99).

## Что сделать
1. `BaseRoles`: `MG = 10` (после `RESIDENT`), `MG_GUN = "szguns:pkm"`, `machineGunners(people, mgPoints)` —
   1, если `mgPoints > 0` и `people >= 3`, иначе 0.
2. Мир: `StalkerEntity.ROLE_MG = BaseRoles.MG`; `NpcManager.spawnAtPrefabPoint` — если `machineGunners(wanted, мест mg)`
   больше, чем пулемётчиков на узле, первым ставить NPC роли MG на место `mg` с `MG_GUN` в руках, неподвижно
   (как часовой), сектор — куда смотрит место (на север).
3. Чек-лист `docs/checklist/056-machine-gunner.md`: на блокпосте уровня 2 у пулемётной точки стоит пулемётчик с ПКМ.

## Готово, когда
`MachineGunnerTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 056 в `docs/tasks/README.md` — «готово (конвейер)».
