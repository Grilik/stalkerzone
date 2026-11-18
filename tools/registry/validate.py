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
VALID_STATUSES = {
    "PLANNED", "IN_PROGRESS", "CODE_COMPLETE", "TESTS_PASSED",
    "GAME_VERIFIED", "ACCEPTED", "BLOCKED", "DEFERRED", "REJECTED"
}

# Допустимые типы сущностей
VALID_TYPES = {"requirement", "task", "subtask"}

def validate_registry(registry_path):
    """Проверить целостность реестра."""
    errors = []
    warnings = []

    # Загрузить реестр
    try:
        with open(registry_path, encoding="utf-8") as f:
            registry = json.load(f)
    except Exception as e:
        return [f"Failed to load registry: {e}"], []

    entities = registry.get("entities", [])

    # 1. Проверка уникальности ID
    ids = [e.get("id") for e in entities]
    id_counts = Counter(ids)
    duplicates = [id_ for id_, count in id_counts.items() if count > 1]
    if duplicates:
        errors.append(f"Duplicate IDs found: {', '.join(duplicates)}")

    # 2. Проверка обязательных полей
    for i, entity in enumerate(entities):
        if "id" not in entity:
            errors.append(f"Entity #{i}: missing 'id' field")
        if "type" not in entity:
            errors.append(f"Entity {entity.get('id', f'#{i}')}: missing 'type' field")
        if "status" not in entity:
            errors.append(f"Entity {entity.get('id', f'#{i}')}: missing 'status' field")

    # 3. Проверка типов сущностей
    invalid_types = [e.get("id") for e in entities if e.get("type") not in VALID_TYPES]
    if invalid_types:
        errors.append(f"Invalid entity types: {', '.join(invalid_types[:10])}")

    # 4. Проверка статусов
    invalid_statuses = [
        f"{e.get('id')}:{e.get('status')}"
        for e in entities
        if e.get("status") not in VALID_STATUSES
    ]
    if invalid_statuses:
        errors.append(f"Invalid statuses: {', '.join(invalid_statuses[:10])}")

    # 5. Проверка связей (существуют ли связанные сущности)
    all_ids = set(ids)
    for entity in entities:
        eid = entity.get("id", "?")

        # Проверить linked_tasks
        for task_id in entity.get("linked_tasks", []):
            if task_id and task_id not in all_ids:
                warnings.append(f"{eid}: linked task '{task_id}' not found in registry")

        # Проверить linked_requirements
        for req_id in entity.get("linked_requirements", []):
            if req_id and req_id not in all_ids:
                warnings.append(f"{eid}: linked requirement '{req_id}' not found in registry")

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
    registry_path = Path(__file__).parent.parent.parent / "docs" / "project-tasks.json"
    errors, warnings = validate_registry(registry_path)
    sys.exit(1 if errors else 0)
