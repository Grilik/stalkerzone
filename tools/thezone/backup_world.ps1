# Резервное копирование рабочей копии TheZone (Э1-03)
# Использование: .\backup_world.ps1 [-Label "описание"] [-SkipPreimages]
#
# Копирует region/, level.dat, entities/ И stalkerzone/ (метка WORKCOPY,
# журнал edits.jsonl, предобразы backup/). Без stalkerzone/ откат уничтожил бы
# журнал правок — поэтому она копируется всегда, кроме явного -SkipPreimages
# (тогда не копируются только предобразы backup/, журнал остаётся).

param(
    [string]$WorldPath = "C:\MineStalker\TheZone-work",
    [string]$BackupRoot = "C:\MineStalker\08_BACKUPS\thezone-backups",
    [string]$Label = "",
    [switch]$SkipPreimages
)

$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$backupName = if ($Label) { "${timestamp}_${Label}" } else { $timestamp }
$backupPath = Join-Path $BackupRoot $backupName

Write-Host "=== РЕЗЕРВНОЕ КОПИРОВАНИЕ THEZONE ===" -ForegroundColor Cyan
Write-Host "Источник: $WorldPath"
Write-Host "Резерв:   $backupPath"
Write-Host ""

# Проверка источника
if (-not (Test-Path $WorldPath)) {
    Write-Host "ОШИБКА: Рабочая копия не найдена: $WorldPath" -ForegroundColor Red
    exit 1
}

# Это должна быть именно рабочая копия, а не оригинал
$marker = Join-Path $WorldPath "stalkerzone\WORKCOPY"
if (-not (Test-Path $marker)) {
    Write-Host "ОШИБКА: нет метки stalkerzone\WORKCOPY — это не рабочая копия." -ForegroundColor Red
    Write-Host "Оригинал 'The Zone' резервировать этим скриптом не нужно (он неизменен)." -ForegroundColor Red
    exit 1
}

if (Test-Path $backupPath) {
    Write-Host "ОШИБКА: резерв уже существует: $backupPath" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $BackupRoot)) {
    New-Item -ItemType Directory -Path $BackupRoot | Out-Null
}
New-Item -ItemType Directory -Path $backupPath | Out-Null

function Copy-Part {
    param([string]$RelPath, [string]$Description)
    $src = Join-Path $WorldPath $RelPath
    if (-not (Test-Path $src)) {
        Write-Host "$Description — нет, пропуск" -ForegroundColor DarkGray
        return
    }
    Write-Host "$Description..." -NoNewline
    $dst = Join-Path $backupPath $RelPath
    $parent = Split-Path $dst -Parent
    if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent | Out-Null }
    Copy-Item -Path $src -Destination $dst -Recurse -Force
    Write-Host " OK" -ForegroundColor Green
}

Write-Host "--- мир ---"
Copy-Part "region"      "Копирование region/"
Copy-Part "level.dat"   "Копирование level.dat"
Copy-Part "entities"    "Копирование entities/"

Write-Host "--- рабочая копия (журнал) ---"
Copy-Part "stalkerzone\WORKCOPY"   "Копирование метки WORKCOPY"
Copy-Part "stalkerzone\edits.jsonl" "Копирование журнала edits.jsonl"

if ($SkipPreimages) {
    Write-Host "Предобразы backup/ — пропущены (-SkipPreimages)" -ForegroundColor DarkGray
} else {
    # предобразы world_edit_log.py кладёт в <мир>/backup/<id>-<время>/
    Copy-Part "backup" "Копирование предобразов backup/"
}

# MD5 контрольные суммы — по всему резерву, до checksums.md5
Write-Host ""
Write-Host "Вычисление MD5..." -NoNewline
$md5File = Join-Path $backupPath "checksums.md5"
$files = Get-ChildItem -Path $backupPath -Recurse -File
$md5List = foreach ($file in $files) {
    $hash = (Get-FileHash -Path $file.FullName -Algorithm MD5).Hash
    $relativePath = $file.FullName.Substring($backupPath.Length + 1)
    "$hash  $relativePath"
}
$md5List | Out-File -FilePath $md5File -Encoding utf8
Write-Host " OK ($($files.Count) файлов)" -ForegroundColor Green

# Проверка сразу после создания: перечитываем все файлы
Write-Host "Проверка резерва..." -NoNewline
$bad = 0
foreach ($line in (Get-Content $md5File)) {
    $parts = $line -split "  ", 2
    if ($parts.Count -ne 2) { continue }
    $filePath = Join-Path $backupPath $parts[1]
    if (-not (Test-Path $filePath)) { $bad++; continue }
    if ((Get-FileHash -Path $filePath -Algorithm MD5).Hash -ne $parts[0]) { $bad++ }
}
if ($bad -gt 0) {
    Write-Host " ОШИБКА: $bad файлов не совпало" -ForegroundColor Red
    exit 1
}
Write-Host " OK" -ForegroundColor Green

# Сводка
$backupSize = (Get-ChildItem -Path $backupPath -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB
Write-Host ""
Write-Host "=== РЕЗЕРВ СОЗДАН ===" -ForegroundColor Green
Write-Host "Путь:   $backupPath"
Write-Host "Размер: $([math]::Round($backupSize, 1)) МБ"
Write-Host "Файлов: $($files.Count)"
Write-Host ""
Write-Host "Для отката: .\restore_world.ps1 -BackupName '$backupName'"
