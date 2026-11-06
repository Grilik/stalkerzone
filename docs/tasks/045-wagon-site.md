# 045. Площадки «wagon» (О7 и дорожный мост, узел 19) и «dump» (свалка А6 и застава О12, узел 20)

**Конвейер:** да
**Тест:** `docs/tasks/tests/WagonSiteTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/zonemap/build/Prefabs.java`
**Чек-лист:** нет

Запросы «Строителя» (журнал 06.11, PR #83 и #86): сборщики `KordonWagon`, `KordonRoadBridge` и `KordonDump` готовы, но к миру
не подключены — узлов 19 и 20 не было (`SiteTest` требует, чтобы центр узла лежал в его площадке). Запускать после 044 и 059.

## Причина (найдена «Программистом»)
В `Prefabs.SITES` нет площадок узлов 19 и 20, поэтому `NpcManager.prefabPoints` (через `Prefabs.site` / `Prefabs.points(level, g, lvl, node)`)
отдаёт узлам `null` — О7 и О12 без своих мест и без построек в мире.

## Что сделать
1. В `Prefabs`: `NODE_WAGON = 19` рядом с `NODE_SOUTH_POST`; в конец `SITES` — строка «Строителя»:
   `new Site("wagon", NODE_WAGON).plateau(104, -88, 130, -60, Site.AUTO, 6).shaper(KordonRoadBridge.RAVINE)`
   `.build(of(KordonWagon.CLEAR, KordonWagon::build)).build(of(KordonRoadBridge.CLEAR, KordonRoadBridge::build))`.
2. Строка «Строителя» для свалки: `new Site("dump", 20).plateau(-54, -134, -22, -108, Site.AUTO, 6).build(of(KordonDump.CLEAR, KordonDump::build))`.
3. Больше ничего: `atp`, `elevator`, заставы туннеля подключает «Строитель» вместе со своими
   сборщиками (П-21, П-22, П-23; пустая площадка без сборщика стёрла бы воксели оригинала); `lair` — карточка 053.

## Готово, когда
`WagonSiteTest` зелёный, `bash tools/conveyor/check.sh` зелёный (`SiteTest`, `PrefabsTest`, `KordonO7Test`);
статус 045 в `docs/tasks/README.md` — «готово (конвейер)».
