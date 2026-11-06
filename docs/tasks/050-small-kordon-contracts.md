# 050. Контракты «Малого Кордона»: зачистка Л3 и доставка на заставу О7

**Конвейер:** да
**Тест:** `docs/tasks/tests/SmallKordonContractsTest.java`
**Файлы:** `src/main/java/ru/stalkerzone/sim/ContractBoard.java`
**Чек-лист:** нет

Запускать после 044 и 047. Контракт «артефакт для Сидоровича» уже есть (`generateKordon`, FETCH_ARTIFACT торговцев);
отдельный «артефакт именно из А6» симуляция не различает — не делать.

## Причина (найдена «Программистом»)
`ContractBoard.generateKordon` не знает новых узлов: заказ на Л3 возникает только случайно (общий цикл «логова рядом
со своими землями» с шансом 5 % и общим лимитом открытых), доставки на заставу нет совсем.

## Что сделать
В `generateKordon` после заказа одиночек на элеватор:
1. Логово на `KordonScenario.DUMP_FOREST` с силой ≥ `KordonBalance.CONTRACT_LAIR_MIN_STRENGTH` и без контракта на него
   (`anyLairContract`) — одиночки (`loners.members >= CONTRACT_MIN_LONERS`) выставляют `CLEAR_LAIR` через `issueCash`
   (награда `700 + сила × 35`, 25 дней), `c.node = 21`.
2. Застава О7 у одиночек и нет их открытого `SUPPLY` на узел 19 — с шансом 0,15 `SUPPLY` через `issueCash`:
   патроны (60) или медикаменты (6), награда `цена × кол-во × 1,45` (цена — `s.sellPrice` у деревни), 15 дней, `c.node = 19`.
Проверять `s.nodes.size() > узел`, чтобы доска не падала на других сценариях. Оплата только из кассы заказчика (залог).

## Готово, когда
`SmallKordonContractsTest` зелёный, `bash tools/conveyor/check.sh` зелёный (`KordonContractsTest`); статус 050 в
`docs/tasks/README.md` — «готово (конвейер)».
