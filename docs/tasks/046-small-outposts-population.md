# 046. Население малых застав (О7, О12, входы туннеля): одиночки, 2–4 человека, логово там не заводится

**Конвейер:** да
**Тест:** `docs/tasks/tests/SmallOutpostsTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/NodePopulation.java`, `src/main/java/ru/stalkerzone/sim/KordonBalance.java`, `src/main/java/ru/stalkerzone/sim/KordonScenario.java`, `src/main/java/ru/stalkerzone/sim/Simulation.java`
**Чек-лист:** нет

Модель — `docs/claude/01-malyi-kordon.md`, раздел 3 «О7: 2–4 человека». Запускать после 044, 059, 045.
Распорядок (сон, костёр, пост) уже даёт мир по местам площадки (`BaseRoles`, `WorkScheduleGoal`, карточки 031–033).

## Причина (найдена «Программистом»)
1. `NodePopulation.visible`: потолок для не-баз `NODE_CAP = 6` — на заставе О7 стоит 6 человек вместо 2–4.
2. Набег мутантов выбивает людей с заставы (`Simulation`, «Мутанты захватили …» / «не удержали …») и заводит там логово
   (`lairAllowed`) — застава остаётся ничьей навсегда (на прототипе: сид 4, О12 пуста с 25-го дня).

## Что сделать
1. `KordonScenario`: `public static final int[] SMALL_OUTPOSTS = {WAGON, DUMP, TUNNEL_SOUTH, TUNNEL_NORTH}` и
   `isSmallOutpost(int id)`; `NodePopulation.number` для таких узлов — не больше 4.
2. `Simulation`: поле `public int[] lairForbidden = {}`, проверка в `lairAllowed`; `KordonScenario` ставит
   `sim.lairForbidden = SMALL_OUTPOSTS`.
3. `KordonBalance.LonerBrain.decide` (именно `LonerBrain`, не первый попавшийся мозг): ничья малая застава без логова при
   `f.members >= 10` снова у одиночек (`n.owner = f.id`).

## Готово, когда
`SmallOutpostsTest` зелёный, `bash tools/conveyor/check.sh` зелёный; статус 046 в `docs/tasks/README.md` — «готово (конвейер)».
