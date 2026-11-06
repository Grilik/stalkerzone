# 054. «Малый Кордон»: из военных — только гарнизон южного блокпоста; полная война — сценарий kordon_war

**Конвейер:** да
**Тест:** `docs/tasks/tests/SmallKordonFactionsTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/Faction.java`, `src/main/java/ru/stalkerzone/sim/Simulation.java`, `src/main/java/ru/stalkerzone/sim/KordonScenario.java`, `src/main/java/ru/stalkerzone/sim/Scenarios.java`, `src/main/java/ru/stalkerzone/sim/ContractBoard.java`, `src/main/java/ru/stalkerzone/sim/Psi.java`, `src/main/java/ru/stalkerzone/anomaly/PsiField.java`, `src/main/java/ru/stalkerzone/world/ZoneWorld.java`, `src/main/java/ru/stalkerzone/world/LevelPoints.java`, `src/main/java/ru/stalkerzone/world/xray/LevelRegions.java`
**Чек-лист:** нет

Решения пользователя 06.11 (П-17, 23:50). Запускать **после 060** (обе поднимают версию сохранения).

## Причина (найдена «Программистом»)
В сценарии «kordon» идёт полная война трёх группировок: военные держат мост, водят конвои и захватывают узлы.

## Что сделать
1. `Faction`: поле `public boolean garrisonOnly` (роль сценария, не сохраняется). `Simulation.think`: при `garrisonOnly` —
   сразу `return` (ИИ не действует: ни войн, ни отрядов, ни захвата).
2. `Scenarios`: `KORDON_WAR = "kordon_war"` → `KordonScenario.create(seed, true)`; `isKordon(id)` — «kordon» или «kordon_war».
   Все проверки `Scenarios.KORDON.equals(...)` / `.equals(Scenarios.KORDON)` / `scenario.equals("kordon")` в файлах из
   паспорта — на `Scenarios.isKordon(...)`.
3. `KordonScenario`: `create(seed)` = `create(seed, false)`; новый `create(long seed, boolean war)` — прежний код плюс:
   `MILITARY.garrisonOnly = !war`; мост (`bridge.owner = MILITARY`) — только при `war`.
4. `Simulation`: версия сохранения +1; Кордон (`isKordon`) старее — новый мир с тем же сидом.

Общие тесты войны уже переведены на `kordon_war` через помощник `src/test/java/KordonWar.java` (до 054 он берёт «kordon»).

## Готово, когда
`SmallKordonFactionsTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 054 в `docs/tasks/README.md` — «готово (конвейер)».
