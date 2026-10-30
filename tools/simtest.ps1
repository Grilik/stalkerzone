# Тесты симуляции на Windows — то же, что шаг "Sim tests" в .github/workflows/build.yml
# Запуск из корня репозитория: powershell -File tools/simtest.ps1
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
if (Test-Path out) { Remove-Item -Recurse -Force out }
New-Item -ItemType Directory -Path out | Out-Null

$src = @()
$src += Get-ChildItem -Recurse -Filter *.java src/main/java/ru/stalkerzone/sim | ForEach-Object { $_.FullName }
$src += Get-ChildItem -Recurse -Filter *.java src/main/java/ru/stalkerzone/zonemap | ForEach-Object { $_.FullName }
$src += (Resolve-Path src/main/java/ru/stalkerzone/world/ZoneBounds.java).Path
$src += (Resolve-Path src/main/java/ru/stalkerzone/artifact/ArtifactDef.java).Path
$src += Get-ChildItem -Filter *.java src/test/java | ForEach-Object { $_.FullName }

$list = Join-Path $env:TEMP "stalker_sources.txt"
$lines = $src | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' }
# без BOM: javac не понимает метку BOM в @-файле
[System.IO.File]::WriteAllLines($list, [string[]]$lines, (New-Object System.Text.UTF8Encoding($false)))
javac --release 17 -encoding UTF-8 -d out "@$list"
if ($LASTEXITCODE -ne 0) { Write-Host "Компиляция не прошла"; exit 1 }

$failed = @()
foreach ($t in Get-ChildItem -Filter *Test.java src/test/java) {
    $name = $t.BaseName
    Write-Host "=== $name"
    java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -cp out $name
    if ($LASTEXITCODE -ne 0) { $failed += $name }
}
if ($failed.Count -gt 0) { Write-Host "Упали: $($failed -join ', ')"; exit 1 }
Write-Host "Все тесты прошли"
