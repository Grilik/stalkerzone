# tools/paths.ps1 - Логические имена путей для скриптов Living Zone
# Обновлен А34: реструктуризация локальных папок

# Корневая директория проекта
$ROOT = "C:\MineStalker"

# Миры и резервные копии
$world_golden = "$ROOT\world\golden"      # Золотая копия TheZone (неизменяемая)
$world_work = "$ROOT\world\work"          # Рабочая копия для разработки
$world_survey = "$ROOT\world\survey"      # Растры и мозаика съемки
$backups = "$ROOT\backups"                # Резервные копии миров

# Игровые данные
$anomaly = "$ROOT\dumps\anomaly"          # Распакованные файлы Anomaly (исходные игры)
$dumps_an_cut = "$ROOT\dumps\an_cut"      # Вырезки из Anomaly

# Сборка и тестирование
$modpack = "$ROOT\modpack"                # Сборка модпака
$playtest = "$ROOT\playtest"              # Тестовые данные (сундуки и т.д.)

# Документация (вне git)
$docs_local = "$ROOT\docs-local"          # Локальная документация

# Карантин и архивы
$quarantine = "$ROOT\quarantine"          # Устаревшие и проблемные файлы

# Репозиторий
$repo = "$ROOT\stalkerzone-git"           # Git репозиторий проекта

# Служебные пути
$tools = "$ROOT\tools"                    # Утилиты и скрипты

# Проверка существования путей
function Test-Paths {
    param([switch]$Critical)
    
    $paths = @{
        world_golden = $world_golden
        world_work = $world_work
        world_survey = $world_survey
        backups = $backups
        anomaly = $anomaly
        dumps_an_cut = $dumps_an_cut
        modpack = $modpack
        playtest = $playtest
        docs_local = $docs_local
        quarantine = $quarantine
        repo = $repo
        tools = $tools
    }
    
    $critical_names = @("world_golden", "world_work", "modpack", "repo")
    
    Write-Host "Пути Living Zone:"
    foreach ($name in $paths.Keys | Sort-Object) {
        $path = $paths[$name]
        $exists = Test-Path $path
        $mark = if ($exists) { "✓" } else { "✗" }
        $crit = if ($name -in $critical_names) { " [CRITICAL]" } else { "" }
        Write-Host "  $mark ${name,-18} $path$crit"
    }
    
    if ($Critical) {
        $missing = $critical_names | Where-Object { -not (Test-Path $paths[$_]) }
        if ($missing) {
            Write-Warning "Отсутствуют критичные пути: $($missing -join ', ')"
            return $false
        }
    }
    return $true
}
