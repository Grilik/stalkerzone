# Резервное копирование рабочей копии TheZone
# Использование: .\backup_world.ps1 [-Label "описание"]

param(
    [string]$WorldPath = "C:\MineStalker\world\work",
    [string]$BackupRoot = "C:\MineStalker\backups\thezone-backups",
    [string]$Label = ""
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

# Создание папки резервов
if (-not (Test-Path $BackupRoot)) {
    New-Item -ItemType Directory -Path $BackupRoot | Out-Null
}

# Копирование
Write-Host "Копирование region/..." -NoNewline
$regionSrc = Join-Path $WorldPath "region"
$regionDst = Join-Path $backupPath "region"
Copy-Item -Path $regionSrc -Destination $regionDst -Recurse -Force
Write-Host " OK" -ForegroundColor Green

Write-Host "Копирование level.dat..." -NoNewline
Copy-Item -Path (Join-Path $WorldPath "level.dat") -Destination $backupPath -Force
Write-Host " OK" -ForegroundColor Green

if (Test-Path (Join-Path $WorldPath "entities")) {
    Write-Host "Копирование entities/..." -NoNewline
    $entitiesSrc = Join-Path $WorldPath "entities"
    $entitiesDst = Join-Path $backupPath "entities"
    Copy-Item -Path $entitiesSrc -Destination $entitiesDst -Recurse -Force
    Write-Host " OK" -ForegroundColor Green
}

# MD5 контрольные суммы
Write-Host ""
Write-Host "Вычисление MD5..." -NoNewline
$md5File = Join-Path $backupPath "checksums.md5"
$files = Get-ChildItem -Path $backupPath -Recurse -File
$md5List = @()
foreach ($file in $files) {
    $hash = (Get-FileHash -Path $file.FullName -Algorithm MD5).Hash
    $relativePath = $file.FullName.Substring($backupPath.Length + 1)
    $md5List += "$hash  $relativePath"
}
$md5List | Out-File -FilePath $md5File -Encoding utf8
Write-Host " OK ($($files.Count) файлов)" -ForegroundColor Green

# Сводка
$backupSize = (Get-ChildItem -Path $backupPath -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB
Write-Host ""
Write-Host "=== РЕЗЕРВ СОЗДАН ===" -ForegroundColor Green
Write-Host "Путь:   $backupPath"
Write-Host "Размер: $([math]::Round($backupSize, 1)) МБ"
Write-Host "MD5:    checksums.md5"
Write-Host ""
Write-Host "Для отката используй: .\restore_world.ps1 -BackupName '$backupName'"

