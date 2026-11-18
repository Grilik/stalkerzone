# Stalker Zone — указания для Qwen Code

Мод S.T.A.L.K.E.R. для Minecraft Forge 1.20.1. Отвечать по-русски.

**Запускать Qwen надо из корня репозитория** — `C:\MineStalker\stalkerzone-git`. Если запустить из
`C:\MineStalker`, Qwen ищет контекстные файлы и проект в родительской папке и репозитория не видит.

## Куда смотреть

- **Задачи и правила работы — `docs/qwen/README.md`**. Начинать оттуда.
- Очередь задач: `docs/qwen/tasks-full.md` (спецификации), `docs/qwen/tasks-short.md` (CLI-промпты).
- Таблица прогресса: `docs/qwen/progress.md`.
- Дорожная карта: `docs/roadmap.md`; архитектурные решения: `docs/claude/decisions/`;
  текущее состояние: `docs/claude/00-start-here.md`.
- Правила проекта для Claude — `CLAUDE.md`; они действуют и для Qwen.

## Как устроены тесты (не JUnit!)

JUnit в проекте нет. Каждый `src/test/java/<Имя>Test.java` — класс с `public static void main`,
счётчиком `f` и `ck(условие, сообщение)`; провал = `System.exit(1)`.

```bash
mkdir -p out
javac --release 17 -encoding UTF-8 -d out $(bash tools/ci/test_sources.sh)   # все тесты одним javac
java -Dfile.encoding=UTF-8 -cp out NodeCountTest
```

- Не скомпилировавшийся тест ломает **весь** набор, поэтому новый тест обязан компилироваться сразу.
- Запускаются только `src/test/java/*Test.java` — плоско, без пакетов и подпапок.
- Сборка мода: `./gradlew build`.

## Границы

- Можно: править код, добавлять тесты, коммитить и пушить в свою ветку, собирать, гонять тесты.
- Нельзя: пушить в `main` (только через PR), удалять файлы, трогать `C:\MineStalker\The Zone\`
  и файлы `*.szl` (данные оригинальной игры), менять `level-type`/`seed` в тестах без согласования.
- Падающий тест не ослаблять (пороги, `audit()`, проверки) — это условие задачи, а не препятствие.
  Не получается — остановиться и описать проблему в `docs/qwen/progress.md`.
