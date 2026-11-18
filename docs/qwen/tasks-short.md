# CLI промпты для Qwen

Короткие промпты — по одному на задачу. Полная спецификация: `docs/qwen/tasks-full.md`.
Запускать из корня репозитория `C:\MineStalker\stalkerzone-git`:

```bash
qwen --approval-mode auto-edit "…промпт…"
```

JUnit в проекте нет: тесты — классы с `main` и `ck(...)`, собираются одним `javac` через
`bash tools/ci/test_sources.sh`. Падать надо по условию `ck`, а не ошибкой компиляции.

---

## К1 (Э4-07): NodeCountTest — делать первым

```
Прочитай docs/qwen/tasks-full.md, раздел К1, и src/test/java/NodeCountTest.java (сейчас красный).
Массивы People.zombies, Politics.cutDays и Simulation.knowledge объявлены как new ...[64]; на 300 узлах
симуляция падает Index 64 в seedAnomalies. Сделай их размер от nodes.size(): инициализация из конструктора
Simulation (в People и Politics добавь init(int nodeCount)). Собери и прогони:
  mkdir -p out && javac --release 17 -encoding UTF-8 -d out $(bash tools/ci/test_sources.sh)
  java -Dfile.encoding=UTF-8 -cp out NodeCountTest
Добейся «ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ». Потом прогони весь набор (цикл из docs/qwen/README.md) — падать должны
только MutantLedgerTest и ZoneBenchTest. Коммит: «К1 (Э4-07): массивы симуляции по числу узлов».
```

## К15 (Э4-09): ZoneBenchTest — после К1

```
Прочитай docs/qwen/tasks-full.md, раздел К15, и src/test/java/ZoneBenchTest.java.
Сначала выполни К1, если он ещё не сделан. Тест гоняет 30 дней × 3 сида на синтетическом атласе 400 узлов
(BigAtlas), проверяет отсутствие исключений, сходимость audit() и медиану шага ≤ 15 мс.
Прогон: java -Dfile.encoding=UTF-8 -cp out ZoneBenchTest (после сборки из test_sources.sh).
Порог 15 мс НЕ ослабляй и audit() не отключай; если медиана выше — остановись и напиши в отчёт цифры.
Коммит: «К15 (Э4-09): бенчмарк ядра на 400 узлах».
```

## К16 (Э8-10): MutantLedgerTest — независимо от К1/К15

```
Прочитай docs/qwen/tasks-full.md, раздел К16, и src/test/java/MutantLedgerTest.java.
Формула Lair.mutantCount() уже верна — её не трогай. Нужна третья книга Ledger: long mutantsBorn и
long mutantsDied, инвариант Σ mutantCount() по логовам = старт + born − died каждый день.
Считай раз в день в Simulation после lairsDaily(), сравнивая снимок по логовам. Сохранение — дописать
в конец и поднять SAVE_VERSION. Тест читает поля по имени, поэтому имена не меняй.
Собери и прогони java -Dfile.encoding=UTF-8 -cp out MutantLedgerTest до «ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ».
Коммит: «К16 (Э8-10): книга мутантов в Ledger».
```

---

## Остальные К-карты (К2–К14)

Заполнится, когда для них будут падающие тесты (этап 3).
