# Э6-01-К2 — Единый реестр населения (исполнитель: Qwen Code)
Источник объёма: docs/tasks/Э6-01-К2-person-registry.md. Карточка главнее этого промпта: при расхождении следовать карточке и сообщить штабу.
Роль: исполнитель (docs/handoff/RULES.md). Одна карточка = один PR. Лимит 2 попытки -> стоп и эскалация в штаб.
Навыки: docs/handoff/SKILLS.md — якоря из вывода grep, py_compile до запуска скриптов реестра, вызов как в CI, стоп при красной сборке.

## Контекст
Э2-17 (К10) слита PR #240: Group и GroupRelations есть; PersonId намеренно не делался — он здесь.
Смежные карточки Э6-01: К3 (population-ledger) и К13 (death-inheritance) — НЕ трогать, это отдельные PR.

## Объём
Строго по карточке К2, ничего сверх неё. Расширения и «заодно улучшил» запрещены.

## Границы файлов
sim/* и src/test/java/*Test.java — плоско, без пакетов (AGENTS.md: запускаются только плоские тесты).
Не трогать: world/*, client/*, net/*, block/*, zonemap/build/*, .github/*.
Если карточка требует копию теста в docs/tasks/tests/ — править обе копии идентично (Э0-13, ConveyorTest).
Версию сохранения поднимать только если карточка этого требует; тогда SaveResetTest обновляется в обеих копиях с ДОБАВЛЕНИЕМ проверки, не ослаблением.

## Тест до работы
Тест из карточки (плоский путь): сначала красный, затем реализация. Все существующие 102 теста остаются зелёными.

## Проверки (bash обязателен в PATH, иначе simtest берёт неполный запасной список — долг А46)
$gitbash = Join-Path (Split-Path (Split-Path (Get-Command git).Source)) 'bin\bash.exe'
$env:PATH = (Split-Path $gitbash) + ";" + $env:PATH
powershell -File tools\simtest.ps1
python tools/registry/validate.py
python tools/registry/check_links.py
python tools/registry/check_deps.py
python tools/registry/gen.py --check

## Реестр и завершение
В начале: Э6-01 -> IN_PROGRESS (скрипт правки реестра: from datetime import datetime, py_compile до запуска).
В конце: коммит, push, gh pr create. TESTS_PASSED ставить ТОЛЬКО вместе с pr_number слитого PR, иначе оставить IN_PROGRESS.
Отчёт штабу: объём, результаты проверок, отступления от карточки с обоснованием.
