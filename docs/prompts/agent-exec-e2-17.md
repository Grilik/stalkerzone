# Э2-17 — группы на Кордоне (исполнитель: Qwen Code)
Роль: исполнитель (docs/handoff/RULES.md). Одна карточка = один PR. Лимит 2 попытки -> стоп и эскалация.
Объём: уровень «группа» на Кордоне. Вольные сталкеры (Person.faction=none) — хаб деревня новичков;
одиночки — основная база Свиноферма + зависимые группы (лагерь у вагончика, дыра в заборе, проводники);
бандиты — конкурирующие банды (АТП и др.). У группы: лидер, база, бюджет, запасы, свои GroupRelations;
раскол после гибели лидера; переход человека между группами — событие без дубля PersonId.
Читать до работы: docs/spec/faction-hierarchy.md, docs/spec/neutral-bases.md, docs/thezone/11-group-links.md,
sim/Faction, sim/People, sim/Person, sim/KordonScenario, docs/project-tasks.json#Э2-17.
Границы файлов: только sim/* и src/test/java; НЕ трогать world/*, client/*, net/*, zonemap/build/*, .github/*.
Тест до работы: новый GroupRelationsTest (красный), затем реализация; существующие sim-тесты остаются зелёными.
Критерии приёмки: группы различимы (лидер/база/бюджет/запасы); связь ведущая->зависимая и доля по идеологии;
раскол и переход без дублей; аудит Ledger сходится; соак 365 дней без снежного кома.
Проверки: python tools/registry/validate.py; check_links.py; check_deps.py; gen.py --check; sim-тесты (simtest.ps1 / gradlew test).
Завершение: коммит, push, gh pr create; статус TESTS_PASSED ставить ТОЛЬКО вместе с pr_number слитого PR.
