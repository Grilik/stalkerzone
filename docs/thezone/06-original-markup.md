# Разметка оригинала — смарт-террейны уровней Anomaly (Э1-05)

Источник — Anomaly 1.5.x на ПК пользователя (`C:\MineStalker\Anomaly`, решение пользователя 15.11). Файлы игры распакованы вне git в `C:\MineStalker\stalker_dump\anomaly` новым `tools/xray/xdb.py` (чистый Python: оглавление LZHuf, файлы LZO1X; замена `xdb.cpp`, для которого на ПК нет компилятора). Разметку делает `tools/atlas/import_level.py`; выход — `src/main/resources/data/stalkerzone/atlas/original/<уровень>.json`, только ID объектов, типы, группировки и координаты (данных игры нет).

```
python -I tools/xray/xdb.py C:/MineStalker/Anomaly/db/configs/spawns.db0 extract C:/MineStalker/stalker_dump/anomaly
python -I tools/xray/xdb.py C:/MineStalker/Anomaly/db/configs/configs.db0 extract C:/MineStalker/stalker_dump/anomaly configs/misc/simulation
python -I tools/xray/xdb.py C:/MineStalker/Anomaly/db/levels/level_<id>.db0 extract C:/MineStalker/stalker_dump/anomaly level.game
python -I tools/atlas/import_level.py C:/MineStalker/stalker_dump/anomaly src/main/resources/data/stalkerzone/atlas/original
```

## Что в разметке
| Поле | Откуда |
|---|---|
| объекты `smart_terrain` и их место | `all.spawn` (запись ALife: секция, имя, позиция) |
| уровень смарт-террейна | по путям `level.game`, начинающимся с его имени; иначе — по префиксу имени |
| `type` (base, territory, resource, lair, surge), `allowed` (кто может занимать) | `simulation_objects_props.ltx` с наследованием секций |
| `faction` — стартовая группировка | `simulation.ltx`: стартовые отряды смарт-террейна (по слову в имени отряда; мутанты — `monsters`) |
| `points`: `campfire` (kamp), `beds` (sleep), `guards`, `flag` (comander), `patrol` | пути `level.game` (первая точка) |
| `points.camps`, `points.campfires` | `camp_zone`, `campfire` из `all.spawn` — к ближайшему смарт-террейну уровня (≤ 60 м); так размечены уровни ЗП |
| `exit` с `to` | `level_changer` |
| `trader` | объекты с «trader» в имени |
| `field` | `anomal_zone` и статичные аномалии `zone_*`, сведённые по клеткам 32 м (`anomalies` — виды и число) |
| `landmark` с `type: paths_only` | смарт-террейны, которых нет в `all.spawn`, но есть их пути (на Кордоне — Холм за элеватором, Луг у фермы, Опушка) |

`kind` по типу: base → `base`, lair → `lair`, resource → `field`, territory → `outpost`, прочее — `landmark`. Это **черновик**: роли на TheZone (база, блокпост, застава, поле) утверждаются в Э1-07 по правилам блокпостов (Э1-15) и потенциала (Э1-16). Координаты — `original` = [xr_x, xr_y, −xr_z] (Minecraft-ориентация, решение б2), до преобразования на TheZone (Э1-06). Статус всех объектов — `observed`. В `world.json` черновики не входят — мод и `AtlasTest` их не читают.

## Итог (22 уровня, 1 849 объектов)
| Вид | Число |
|---|---|
| базы | 100 (Монолит 21, одиночки 14, наёмники 8, бандиты 7, военные 7, Свобода 7, Долг 5, ЧН 5, учёные 4, ренегаты 3, торговцы 2; без стартовых отрядов — 14) |
| заставы (territory) | 59 |
| логова | 217 |
| аномальные поля | 1 317 (вместе с клетками статичных аномалий) |
| переходы | 91 |
| торговцы | 11 (места торговцев; служебные зоны у дверей исключены) |
| прочие места | 54 |

## Уровень Anomaly → локация TheZone
| Уровень | Локация TheZone (`places.json`, метки `border.json`) |
|---|---|
| l01_escape | Кордон |
| l02_garbage | Свалка |
| l03_agroprom | Агропром |
| l04_darkvalley | Тёмная долина |
| k00_marsh | Болота (лагерь ЧН) |
| l05_bar, l06_rostok | Бар; Дикая территория (метки 6–7 — завод «Росток», Э5-06) |
| l07_military | Армейские склады |
| l08_yantar | Янтарь |
| l09_deadcity | Мёртвый город |
| l10_limansk | Лиманск |
| l10_radar | Радар / Выжигатель (метка 2) |
| l10_red_forest | Рыжий лес (новая локация, Э5-19) |
| l11_hospital | Госпиталь (метка 3) |
| l11_pripyat (ТЧ), pripyat (ЗП) | Припять — 5 частей (север, юг, центр, восток, запад); какая часть какому уровню — по якорям Э1-06 |
| l12_stancia, l12_stancia_2 | ЧАЭС |
| l13_generators | Генераторы (метка 5, база О-Сознания) |
| k01_darkscape | Тёмная лощина (метка 1) |
| zaton | Затон |
| jupiter | Янов (станция) |
| — | Дуга-3 (`places.json`): отдельного уровня нет — разметка по съёмке Э1-04 |
| — | Полигон (метка 4): уровня в Anomaly нет (вырезанная локация ЧН) — Э5-20 |

## Проверка
- **Кордон 1:1:** все 15 узлов `points/l01_escape.json` со смарт-террейном Anomaly совпадают с разметкой после сдвига уровня (`dx 209, dz −142, y +100`): костёр, места сна, часовые, патруль — в пределах 1,5 блока (15 из 15). Узлы без смарт-террейна (бункер Сидоровича, выходы, дыра в заборе) в разметке — торговец `esc_m_trader` и переходы.
- `AtlasTest` (Э1-13) — зелёный.
- `xdb.py` проверен на трёх архивах: оглавление `configs.db0` (3 947 файлов) читается целиком; `all.spawn` (97 МБ, LZO) — размер совпал с оглавлением. Особенность X-Ray: порог перестройки дерева LZHuf `MAX_FREQ = 0x4000` (у Окумуры 0x8000) — с 0x8000 оглавление ломается после ≈ 63 КБ.

## Ограничения
- Смарт-террейны подземелий и служебные (`pri` без путей, `pas`, `sar`, `x162`, `dar` и др., 45 шт.) уровня не получили — в TheZone подземелий этих уровней нет; список печатает скрипт.
- Аномалии и стоянки с префиксом `pri` без путей не различают l11_pripyat и pripyat (ЗП) — не включены.
- Группировка — стартовая, по составу отрядов; 14 баз без стартовых отрядов — без `faction` (есть `allowed`).
