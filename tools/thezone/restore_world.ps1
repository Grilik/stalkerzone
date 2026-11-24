# Откат рабочей копии TheZone из резерва
# Использование: .\restore_world.ps1 -BackupName "2026-11-18_14-30-00"

param(
    [Parameter(Mandatory=$true)]
    [string]$BackupName,
    [string]$WorldPath = "C:\MineStalker\world\work",
    [string]$BackupRoot = "C:\MineStalker\backups\thezone-backups",
    [switch]$Force
)

$backupPath = Join-Path $BackupRoot $BackupName

Write-Host "=== ОТКАТ THEZONE ИЗ РЕЗЕРВА ===" -ForegroundColor Yellow
Write-Host "Резерв: $backupPath"
Write-Host "Цель:   $WorldPath"
Write-Host ""

# Проверка резерва
if (-not (Test-Path $backupPath)) {
    Write-Host "ОШИБКА: Резерв не найден: $backupPath" -ForegroundColor Red
    Write-Host ""
    Write-Host "Доступные резервы:"
    Get-ChildItem -Path $BackupRoot -Directory | ForEach-Object { Write-Host "  - $($_.Name)" }
    exit 1
}

# Проверка MD5
$md5File = Join-Path $backupPath "checksums.md5"
if (Test-Path $md5File) {
    Write-Host "Проверка MD5..." -NoNewline
    $md5Lines = Get-Content $md5File
    $errors = 0
    foreach ($line in $md5Lines) {
        $parts = $line -split "  ", 2
        $expectedHash = $parts[0]
        $filePath = Join-Path $backupPath $parts[1]
        if (Test-Path $filePath) {
            $actualHash = (Get-FileHash -Path $filePath -Algorithm MD5).Hash
            if ($actualHash -ne $expectedHash) {
                $errors++
            }
        } else {
            $errors++
        }
    }
    if ($errors -gt 0) {
        Write-Host " ОШИБКА ($errors файлов повреждено)" -ForegroundColor Red
        if (-not $Force) {
            Write-Host "Используй -Force для принудительного отката"
            exit 1
        }
    } else {
        Write-Host " OK" -ForegroundColor Green
    }
}

# Предупреждение
if (-not $Force) {
    Write-Host ""
    Write-Host "ВНИМАНИЕ: Текущая рабочая копия будет перезаписана!" -ForegroundColor Yellow
    $response = Read-Host "Продолжить? (yes/no)"
    if ($response -ne "yes") {
        Write-Host "Отменено"
        exit 0
    }
}

# Создать резерв текущего состояния
if (Test-Path $WorldPath) {
    Write-Host ""
    Write-Host "Сохранение текущего состояния..." -NoNewline
    $safetyBackup = Join-Path $BackupRoot "before_restore_$(Get-Date -Format 'yyyy-MM-dd_HH-mm-ss')"
    Copy-Item -Path $WorldPath -Destination $safetyBackup -Recurse -Force
    Write-Host " OK" -ForegroundColor Green
    Write-Host "Сохранено в: $safetyBackup"
}

# Откат
Write-Host ""
Write-Host "Удаление текущей копии..." -NoNewline
Remove-Item -Path $WorldPath -Recurse -Force -ErrorAction Stop
Write-Host " OK" -ForegroundColor Green

Write-Host "Восстановление из резерва..." -NoNewline
Copy-Item -Path $backupPath -Destination $WorldPath -Recurse -Force
Write-Host " OK" -ForegroundColor Green

# Удаление checksums.md5 из рабочей копии
$checksumInWorld = Join-Path $WorldPath "checksums.md5"
if (Test-Path $checksumInWorld) {
    Remove-Item -Path $checksumInWorld -Force
}

Write-Host ""
Write-Host "=== ОТКАТ ЗАВЕРШЁН ===" -ForegroundColor Green
Write-Host "Рабочая копия восстановлена из: $BackupName"

