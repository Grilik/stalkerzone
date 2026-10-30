# 008. Смоук-тест сервера одной командой

**Линия: симуляция.**

Выполнять после 001. Модель: обычная. Нужен для проверки всех «мировых» карточек 009–015.

## Что сделать
`tools/smoketest.ps1` (PowerShell 5, без Python):
1. Готовит `run/`: `eula.txt`, `server.properties` с `level-type=stalkerzone\:zone`, `enable-rcon=true`, `rcon.password=sz`,
   `rcon.port=25575`, `max-tick-time=-1`, `level-name=smoke_<дата-время>`; JVM-свойство `-Dstalkerzone.scenario=kordon`
   (через `JAVA_TOOL_OPTIONS` или аргументы `runServer` — как проще без правки `build.gradle`; если без правки нельзя — вопрос в PR).
2. Запускает `./gradlew.bat runServer` в фоне, ждёт в логе `Done (` или `Crash`/`Exception in server tick loop` (таймаут 15 мин).
3. Подключается по RCON (протокол Source RCON на `System.Net.Sockets.TcpClient`, свой маленький клиент в скрипте) и выполняет
   список проверок из `tools/smoke_checks.txt` (по строке: команда и ожидаемый фрагмент ответа / `!фрагмент` = не должно быть):
   `forceload add` вокруг деревни (небольшие квадраты), `zone factions`, `zone step 5`, `zone audit`, `zone events 5`, `zone lairs`, `zone anomalies`.
   Если `/zone ...` не работает из консоли — добавить консольную поддержку только этим подкомандам.
4. `stop`, ждёт завершения. Итог — `run/smoke-report.txt` (PASS/FAIL по каждой строке) и код выхода 0/1.
   В отчёт также попадают все строки лога сервера с `ERROR` и `stalkerzone`.

## Готово, когда
- На чистой копии `powershell -ExecutionPolicy Bypass -File tools/smoketest.ps1` проходит за < 15 мин и выдаёт PASS.
- Искусственно сломанная проверка (неверный фрагмент) даёт FAIL и код 1.
- `TESTING.md` дополнен разделом «Смоук-тест». PR с блоком передачи смены.
