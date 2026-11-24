#!/usr/bin/env python3
"""
Валидатор единого реестра проекта Living Zone.
Проверяет целостность, уникальность ID, корректность ссылок и статусов.
"""
import json
import sys
from pathlib import Path
from collections import Counter

# Допустимые статусы
VALID_STATUSES_BY_TYPE = {
    "task": {"PLANNED", "IN_PROGRESS", "CODE_COMPLETE", "TESTS_PASSED",
             "GAME_VERIFIED", "ACCEPTED", "BLOCKED", "DEFERRED", "REJECTED"},
    "requirement": {"PLANNED", "IN_PROGRESS", "CODE_COMPLETE", "TESTS_PASSED",
                    "GAME_VERIFIED", "ACCEPTED", "BLOCKED", "DEFERRED", "REJECTED"},
    "subtask": {"PLANNED", "IN_PROGRESS", "CODE_COMPLETE", "TESTS_PASSED",
                "GAME_VERIFIED", "ACCEPTED", "BLOCKED", "DEFERRED", "REJECTED"},
    "idea": {"NEW", "DEFERRED", "TAKEN", "REJECTED"}
}

# Допустимые типы сущностей
VALID_TYPES = {"requirement", "task", "subtask", "idea"}

def validate_registry(registry_path):
    """Проверить целостность реестра."""
    errors = []
    warnings = []

    # Загрузить реестр
    try:
        with open(registry_path, encoding="utf-8") as f:
            registry = json.load(f)
    except Exception as e:
        msg = f"Failed to load registry: {e}"
        print(f"❌ {msg}")
        return [msg], []

    entities = registry.get("entities", [])

    # 1. Проверка уникальности ID (C3: None-безопасно)
    ids = [e.get("id") for e in entities]
    id_counts = Counter(ids)
    duplicates = [str(id_) for id_, count in id_counts.items() if count > 1]
    if duplicates:
        errors.append(f"Duplicate IDs found: {', '.join(duplicates)}")

    # 2. Проверка обязательных полей (C3: id, type, title, status, updated — по registry-guide.md)
    required = ("id", "type", "title", "status", "updated")
    for i, entity in enumerate(entities):
        label = entity.get("id", f"#{i}")
        for field in required:
            if field not in entity:
                errors.append(f"Entity {label}: missing '{field}' field")

    # 3. Проверка типов сущностей
    invalid_types = [str(e.get("id")) for e in entities if e.get("type") not in VALID_TYPES]
    if invalid_types:
        errors.append(f"Invalid entity types: {', '.join(invalid_types[:10])}")

    # 4. Проверка статусов
    invalid_statuses = []
    for e in entities:
        et = e.get("type", "?")
        allowed = VALID_STATUSES_BY_TYPE.get(et, set())
        if e.get("status") not in allowed:
            invalid_statuses.append(f"{e.get('id')}:{e.get('status')}")
    if invalid_statuses:
        errors.append(f"Invalid statuses: {', '.join(invalid_statuses[:10])}")

    # 5. Проверка связей (C3: висячие ссылки и не-списки — ошибки целостности, ненулевой exit code)
    all_ids = set(ids)
    for entity in entities:
        eid = entity.get("id", "?")
        for field in ("linked_tasks", "linked_requirements"):
            links = entity.get(field, [])
            if not isinstance(links, list):
                errors.append(f"{eid}: '{field}' must be a list")
                continue
            for target in links:
                if target and target not in all_ids:
                    errors.append(f"{eid}: linked entity '{target}' ({field}) not found in registry")

    # 6. Статистика
    type_counts = Counter(e.get("type") for e in entities)
    status_counts = Counter(e.get("status") for e in entities)

    print(f"\n=== Registry Validation Report ===")
    print(f"Total entities: {len(entities)}")
    print(f"\nBy type:")
    for t, count in sorted(type_counts.items()):
        print(f"  {t}: {count}")
    print(f"\nBy status:")
    for s, count in sorted(status_counts.items(), key=lambda x: -x[1]):
        print(f"  {s}: {count}")

    if errors:
        print(f"\n❌ ERRORS ({len(errors)}):")
        for err in errors[:20]:
            print(f"  - {err}")
        if len(errors) > 20:
            print(f"  ... and {len(errors) - 20} more")

    if warnings:
        print(f"\n⚠️  WARNINGS ({len(warnings)}):")
        for warn in warnings[:20]:
            print(f"  - {warn}")
        if len(warnings) > 20:
            print(f"  ... and {len(warnings) - 20} more")

    if not errors:
        print(f"\n✅ Registry is valid")

    return errors, warnings

if __name__ == "__main__":
    registry_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent.parent / "docs" / "project-tasks.json"
    errors, warnings = validate_registry(registry_path)
    sys.exit(1 if errors else 0)
