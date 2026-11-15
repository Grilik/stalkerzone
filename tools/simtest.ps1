# Тесты симуляции на Windows — те же исходники, что шаг "Sim tests" в .github/workflows/build.yml и конвейер:
# список берётся из tools/ci/test_sources.sh (единый список, Э0-04). Запуск из любого каталога:
#   powershell -ExecutionPolicy Bypass -File tools/simtest.ps1
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path -Parent $PSScriptRoot   # корень репозитория (родитель tools/)
Set-Location $root
if (Test-Path out) { Remove-Item -Recurse -Force out }
New-Item -ItemType Directory -Path out | Out-Null

# Список исходников: tools/ci/test_sources.sh через bash (Git Bash есть на ПК).
# Без bash — запасной список, равный .sh на 14.11.2026 (sim, zonemap, ZoneBounds, ArtifactDef, TaczWeight, тесты).
$src = @()
$listSource = "запасной список"
$bash = Get-Command bash -ErrorAction SilentlyContinue
if ($bash) {
    $ErrorActionPreference = "Continue"
    $src = @(& $bash.Source "tools/ci/test_sources.sh" 2>$null | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne "" })
    $ErrorActionPreference = "Stop"
    if ($src.Count -gt 0) { $listSource = "tools/ci/test_sources.sh" }
}
if ($src.Count -eq 0) {
    Write-Host "bash не найден или список пуст — беру запасной список (должен совпадать с tools/ci/test_sources.sh)"
    $src = @()
    $src += Get-ChildItem -Recurse -Filter *.java src/main/java/ru/stalkerzone/sim | ForEach-Object { $_.FullName }
    $src += Get-ChildItem -Recurse -Filter *.java src/main/java/ru/stalkerzone/zonemap | ForEach-Object { $_.FullName }
    $src += (Resolve-Path src/main/java/ru/stalkerzone/world/ZoneBounds.java).Path
    $src += (Resolve-Path src/main/java/ru/stalkerzone/artifact/ArtifactDef.java).Path
    $src += (Resolve-Path src/main/java/ru/stalkerzone/gear/TaczWeight.java).Path
    $src += Get-ChildItem -Filter *.java src/test/java | ForEach-Object { $_.FullName }
}

$list = Join-Path $env:TEMP "stalker_sources.txt"
$lines = $src | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' }
# без BOM: javac не понимает метку BOM в @-файле
[System.IO.File]::WriteAllLines($list, [string[]]$lines, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Исходников: $($lines.Count) ($listSource)"
javac --release 17 -encoding UTF-8 -d out "@$list"
if ($LASTEXITCODE -ne 0) { Write-Host "Компиляция не прошла"; exit 1 }

$failed = @()
$total = 0
foreach ($t in Get-ChildItem -Filter *Test.java src/test/java) {
    $name = $t.BaseName
    $total++
    Write-Host "=== $name"
    java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -cp out $name
    if ($LASTEXITCODE -ne 0) { $failed += $name }
}
if ($failed.Count -gt 0) { Write-Host "Упали ($($failed.Count) из $total): $($failed -join ', ')"; exit 1 }
Write-Host "Все тесты прошли: $total"
