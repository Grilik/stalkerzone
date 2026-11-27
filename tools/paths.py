# tools/paths.py - Логические имена путей для скриптов Living Zone
# Обновлен А34: реструктуризация локальных папок

from pathlib import Path

# Корневая директория проекта
ROOT = Path("C:/MineStalker")

# Миры и резервные копии
world_golden = ROOT / "world" / "golden"  # Золотая копия TheZone (неизменяемая)
world_work = ROOT / "world" / "work"      # Рабочая копия для разработки
world_survey = ROOT / "world" / "survey"  # Растры и мозаика съемки
backups = ROOT / "backups"                # Резервные копии миров

# Игровые данные
anomaly = ROOT / "games" / "Anomaly"      # Игра Anomaly 1.5.x (установка)
dumps_an_cut = ROOT / "dumps" / "an_cut"  # Вырезки из Anomaly
dumps_stalker = ROOT / "dumps" / "stalker_dump"  # Извлечённый дамп (level.game/configs/textures)

# Сборка и тестирование
modpack = ROOT / "modpack"                # Сборка модпака
playtest = ROOT / "playtest"              # Тестовые данные (сундуки и т.д.)

# Документация (вне git)
docs_local = ROOT / "docs-local"          # Локальная документация

# Карантин и архивы
quarantine = ROOT / "quarantine"          # Устаревшие и проблемные файлы

# Репозиторий
repo = ROOT / "stalkerzone-git"           # Git репозиторий проекта

# Служебные пути
tools = ROOT / "tools"                    # Утилиты и скрипты

# Проверка существования путей
def verify_paths():
    """Проверить, что все критичные пути существуют"""
    critical = [world_golden, world_work, modpack, repo]
    missing = [p for p in critical if not p.exists()]
    if missing:
        print("⚠ Отсутствуют критичные пути:")
        for p in missing:
            print(f"  {p}")
        return False
    return True

if __name__ == "__main__":
    print("Пути Living Zone:")
    for name, path in [
        ("world_golden", world_golden),
        ("world_work", world_work),
        ("world_survey", world_survey),
        ("backups", backups),
        ("anomaly", anomaly),
        ("dumps_an_cut", dumps_an_cut),
        ("dumps_stalker", dumps_stalker),
        ("modpack", modpack),
        ("playtest", playtest),
        ("docs_local", docs_local),
        ("quarantine", quarantine),
        ("repo", repo),
        ("tools", tools),
    ]:
        exists = "✓" if path.exists() else "✗"
        print(f"  {exists} {name:<18} {path}")
