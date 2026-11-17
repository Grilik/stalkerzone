# Откат рабочей копии TheZone из резерва (Э1-03)
# Использование: .\restore_world.ps1 -BackupName "2026-11-18_14-30-00"
#
# Что делает:
#  1. проверяет MD5 всех файлов резерва;
#  2. сохраняет текущее состояние (включая журнал edits.jsonl) в before_restore_*;
#  3. восстанавливает файлы резерва поверх рабочей копии;
#  4. удаляет файлы, которых в резерве нет (кроме служебных).
# Папка рабочей копии целиком НЕ удаляется — журнал и предобразы сохраняются.

param(
    [Parameter(Mandatory=$true)]
    [string]$BackupName,
    [string]$WorldPath = "C:\MineStalker\TheZone-work",
    [string]$BackupRoot = "C:\MineStalker\08_BACKUPS\thezone-backups",
    [switch]$Force
)

$backupPath = Join-Path $BackupRoot $BackupName

Write-Host "=== ОТКАТ THEZONE ИЗ РЕЗЕРВА ===" -ForegroundColor Yellow
Write-Host "Резерв: $backupPath"
Write-Host "Цель:   $WorldPath"
Write-Host ""

if (-not (Test-Path $backupPath)) {
    Write-Host "ОШИБКА: резерв не найден: $backupPath" -ForegroundColor Red
    Write-Host ""
    Write-Host "Доступные резервы:"
    Get-ChildItem -Path $BackupRoot -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Write-Host "  - $($_.Name)" }
    exit 1
}

if (-not (Test-Path $WorldPath)) {
    Write-Host "ОШИБКА: рабочая копия не найдена: $WorldPath" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path (Join-Path $backupPath "stalkerzone\WORKCOPY"))) {
    Write-Host "ОШИБКА: в резерве нет метки stalkerzone\WORKCOPY." -ForegroundColor Red
    Write-Host "Такой резерв неполный (сделан старой версией скрипта) — откат запрещён:" -ForegroundColor Red
    Write-Host "он затрёт журнал edits.jsonl и предобразы backup/." -ForegroundColor Red
    exit 1
}

# --- 1. Проверка MD5 ---
$md5File = Join-Path $backupPath "checksums.md5"
if (Test-Path $md5File) {
    Write-Host "Проверка MD5 резерва..." -NoNewline
    $errors = 0
    foreach ($line in (Get-Content $md5File)) {
        $parts = $line -split "  ", 2
        if ($parts.Count -ne 2) { continue }
        $filePath = Join-Path $backupPath $parts[1]
        if (-not (Test-Path $filePath)) { $errors++; continue }
        if ((Get-FileHash -Path $filePath -Algorithm MD5).Hash -ne $parts[0]) { $errors++ }
    }
    if ($errors -gt 0) {
        Write-Host " ОШИБКА ($errors файлов повреждено)" -ForegroundColor Red
        if (-not $Force) {
            Write-Host "Проверь резерв. Принудительный откат: -Force"
            exit 1
        }
        Write-Host "Продолжаю из-за -Force" -ForegroundColor Yellow
    } else {
        Write-Host " OK" -ForegroundColor Green
    }
} else {
    Write-Host "ВНИМАНИЕ: checksums.md5 нет — целостность не проверена" -ForegroundColor Yellow
}

# --- 2. Подтверждение и safety backup ---
if (-not $Force) {
    Write-Host ""
    Write-Host "ВНИМАНИЕ: рабочая копия будет приведена к состоянию резерва!" -ForegroundColor Yellow
    if ((Read-Host "Продолжить? (yes/no)") -ne "yes") {
        Write-Host "Отменено"
        exit 0
    }
}

Write-Host ""
Write-Host "Сохранение текущего состояния (журнал + мир)..."
$before = @(Get-ChildItem -Path $BackupRoot -Directory | Select-Object -ExpandProperty Name)
& (Join-Path $PSScriptRoot "backup_world.ps1") -WorldPath $WorldPath -BackupRoot $BackupRoot `
    -Label "before_restore" | Out-Null
$after = @(Get-ChildItem -Path $BackupRoot -Directory | Select-Object -ExpandProperty Name)
$safetyBackup = $after | Where-Object { $before -notcontains $_ } | Select-Object -First 1
if (-not $safetyBackup) {
    Write-Host "ОШИБКА: safety backup не создан, откат отменён" -ForegroundColor Red
    exit 1
}
Write-Host "Сохранено в: $(Join-Path $BackupRoot $safetyBackup)" -ForegroundColor Green

# --- 3. Восстановление поверх ---
Write-Host ""
Write-Host "Восстановление файлов резерва..." -NoNewline
$backupFiles = Get-ChildItem -Path $backupPath -Recurse -File |
    Where-Object { $_.Name -ne "checksums.md5" }
foreach ($file in $backupFiles) {
    $rel = $file.FullName.Substring($backupPath.Length + 1)
    $dst = Join-Path $WorldPath $rel
    $parent = Split-Path $dst -Parent
    if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent | Out-Null }
    Copy-Item -Path $file.FullName -Destination $dst -Force
}
Write-Host " OK ($($backupFiles.Count) файлов)" -ForegroundColor Green

# --- 4. Лишние файлы (появились после резерва) ---
$restoredRel = @{}
foreach ($file in $backupFiles) { $restoredRel[$file.FullName.Substring($backupPath.Length + 1)] = $true }
$extra = Get-ChildItem -Path $WorldPath -Recurse -File | Where-Object {
    $rel = $_.FullName.Substring($WorldPath.Length + 1)
    -not $restoredRel.ContainsKey($rel) -and $_.Name -notin @("session.lock", "checksums.md5")
}
if ($extra.Count -gt 0) {
    Write-Host ""
    Write-Host "Файлы, которых нет в резерве (будут удалены): $($extra.Count)" -ForegroundColor Yellow
    $extra | ForEach-Object { Write-Host "  - $($_.FullName.Substring($WorldPath.Length + 1))" }
    if ($Force -or ((Read-Host "Удалить их? (yes/no)") -eq "yes")) {
        $extra | Remove-Item -Force
        Write-Host "Удалены" -ForegroundColor Green
    } else {
        Write-Host "Оставлены (состояние не совпадает с резервом)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== ОТКАТ ЗАВЕРШЁН ===" -ForegroundColor Green
Write-Host "Рабочая копия приведена к резерву: $BackupName"
