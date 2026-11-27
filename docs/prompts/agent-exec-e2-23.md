# Э2-23 — Деревня новичков как нейтральная база (исполнитель: Qwen Code)
Источник объёма: docs/tasks/Э2-23-neutral-village.md и docs/spec/neutral-bases.md. Карточка главнее промпта.
Роль: исполнитель (RULES.md). Одна карточка = один PR. Лимит 2 попытки -> стоп и эскалация.
Навыки: SKILLS.md — якоря из вывода, py_compile до запуска, вызов как в CI, стоп при красной сборке.

Опора: Э2-17/К10 (PR #240) и Э2-21 — Group, GroupRelations, связь договор защиты уже есть, не дублировать.
Ключевое: владелец деревни — поселение, не фракция (исправить village.owner=LONERS); население нейтральной базы не смешивать с гарнизонами NodePopulation.
Границы файлов: sim/* и src/test/java/*Test.java (плоско). Не трогать world/*, client/*, net/*, block/*, zonemap/build/*, .github/*.
Тест до работы: NeutralBaseTest (красный), затем реализация.
Вне объёма: Э6-07, Э6-02, К17, Э2-21, Э2-22.
Проверки: git-bash в PATH, simtest.ps1, 365 дней kordon с audit(), ворота реестра.
Реестр: в начале Э2-23 -> IN_PROGRESS; в конце коммит, push, gh pr create; TESTS_PASSED только с pr_number слитого PR.
