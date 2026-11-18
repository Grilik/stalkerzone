#!/usr/bin/env python3
"""
Утилиты для работы с единым реестром project-tasks.json.
Используется в Dashboard и других инструментах.
"""
import json
from pathlib import Path

# C4: resolve() нормализует «..» в __file__ (build.py добавляет в sys.path путь tools/dashboard/..,
# иначе Path.parent цепочка даёт tools/dashboard/docs вместо корня репозитория)
REGISTRY_PATH = Path(__file__).resolve().parent.parent.parent / "docs" / "project-tasks.json"

# Маппинг новых статусов → Dashboard статусы
STATUS_TO_DASHBOARD = {
    "PLANNED": "todo",
    "IN_PROGRESS": "work",
    "CODE_COMPLETE": "work",
    "TESTS_PASSED": "done",
    "GAME_VERIFIED": "done",
    "ACCEPTED": "done",
    "BLOCKED": "todo",
    "DEFERRED": "later",
    "REJECTED": "dropped"
}

# Маппинг для отображения статусов
STATUS_LABEL = {
    "PLANNED": "запланировано",
    "IN_PROGRESS": "выполняется",
    "CODE_COMPLETE": "код написан",
    "TESTS_PASSED": "тесты пройдены",
    "GAME_VERIFIED": "проверено в игре",
    "ACCEPTED": "принято",
    "BLOCKED": "заблокировано",
    "DEFERRED": "отложено",
    "REJECTED": "отклонено"
}

def load_registry():
    """Загрузить единый реестр."""
    with open(REGISTRY_PATH, encoding="utf-8") as f:
        return json.load(f)

def get_entities_by_type(registry, entity_type):
    """Получить все сущности заданного типа (C2: отсутствующий ключ entities не ломает сборку)."""
    return [e for e in registry.get("entities", []) if e.get("type") == entity_type]

def get_entity_by_id(registry, entity_id):
    """Найти сущность по ID."""
    for entity in registry.get("entities", []):
        if entity.get("id") == entity_id:
            return entity
    return None

def get_linked_requirements(registry, task_id):
    """Получить требования, связанные с задачей."""
    task = get_entity_by_id(registry, task_id)
    if not task:
        return []
    req_ids = task.get("linked_requirements", [])
    return [get_entity_by_id(registry, req_id) for req_id in req_ids if get_entity_by_id(registry, req_id)]

def get_linked_tasks(registry, req_id):
    """Получить задачи, связанные с требованием."""
    req = get_entity_by_id(registry, req_id)
    if not req:
        return []
    task_ids = req.get("linked_tasks", [])
    return [get_entity_by_id(registry, task_id) for task_id in task_ids if get_entity_by_id(registry, task_id)]

def get_status_counts(entities):
    """Подсчитать количество сущностей по статусам."""
    from collections import Counter
    return Counter(e.get("status") for e in entities)

def to_dashboard_status(status):
    """Преобразовать новый статус в Dashboard статус."""
    return STATUS_TO_DASHBOARD.get(status, "todo")
