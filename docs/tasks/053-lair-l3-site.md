# 053. Логово Л3 в мире: площадка «lair» узла 21 со Structures.lairDen

**Конвейер:** да
**Тест:** `docs/tasks/tests/LairDenSiteTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/zonemap/build/Prefabs.java`
**Чек-лист:** нет

Модель — `docs/claude/01-malyi-kordon.md` (площадка **lair**: без плато, `Structures.lairDen` у (−72, −98); сдвинуто на (−92, −100) — западнее площадки `dump`, см. 047). Запускать после 044.

## Причина (найдена «Программистом»)
Площадки у узла 21 нет — в лесу у свалки не видно логова, у мутантов нет точки норы.

## Что сделать
В `Prefabs` (тот же пакет, что `Structures`, `lairDen` доступен):
1. `NODE_LAIR_L3 = 21`; константы `LAIR_L3 = {-92, -100}` и `LAIR_L3_CLEAR` (квадрат ±14 вокруг) — **выше** списка `SITES`
   (иначе «illegal forward reference»).
2. В конец `SITES`: `new Site("lair", NODE_LAIR_L3).build(of(LAIR_L3_CLEAR, Prefabs::lairL3))`, где `lairL3(BlockSink s, int lvl)`
   вызывает `Structures.lairDen(new B(s, LAIR_L3[0], LAIR_L3[1], 0, 2101L, lvl))` и ставит место `s.mark("den", x, s.ground(x, z) + 1, z)` в точке `LAIR_L3`.
   Плато не нужно.

## Готово, когда
`LairDenSiteTest` зелёный, `bash tools/conveyor/check.sh` зелёный (`SiteTest`, `PrefabsTest`); статус 053 в
`docs/tasks/README.md` — «готово (конвейер)».
