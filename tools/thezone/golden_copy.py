#!/usr/bin/env python3
"""Э1-11А: версионируемая золотая копия TheZone (vN) — эталон для кнопки «Зона».

  python -I tools/thezone/golden_copy.py make   <рабочая копия> <каталог золотых копий>
  python -I tools/thezone/golden_copy.py verify <каталог золотых копий> [N]
  python -I tools/thezone/golden_copy.py list   <каталог золотых копий>

make — создаёт следующую версию vN (номера строго возрастают, существующие не перезаписываются):
  копия рабочей копии без runtime-состояния (backup/, stalkerzone/, playerdata/, stats/,
  advancements/, data/stalkerzone_sim.dat, session.lock); level.dat подготавливается как в Э1-08
  (trial_copy.level_dat: команды, точка появления из атласа world.json, игрок там же, spawnRadius 0,
  randomTickSpeed 0); checksums.md5 — формат Э1-03 (backup_world.ps1); golden.json — метаданные версии.
  Сборка во временном каталоге vN.tmp-<pid> → сверка MD5 → атомарное переименование в vN.
  Рабочая копия и оригинал TheZone только читаются. Миры из копии создаёт мод (кнопка «Зона»,
  ru.stalkerzone.world.golden.GoldenCopy) — каждый мир независим, обновления копии его не касаются.

verify/list — целостность версий (точный состав файлов + MD5 каждого), только чтение.
"""
import json
import os
import shutil
import sys
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import trial_copy as T      # noqa: E402  (Э1-08: подготовка level.dat)
import verify_backup as V   # noqa: E402  (Э1-03: MD5 файлов)

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ATLAS = os.path.join(ROOT, "src", "main", "resources", "data", "stalkerzone", "atlas", "world.json")
EXCLUDE_DIRS = {"backup", "stalkerzone", "playerdata", "stats", "advancements"}
EXCLUDE_FILES = {"session.lock", "checksums.md5", "golden.json"}
SIM_SAVE = os.path.join("data", "stalkerzone_sim.dat")


def versions(root):
    out = []
    if os.path.isdir(root):
        for n in os.listdir(root):
            # только канонические имена vN (v01 ≠ v1 — не эталон), как в ядре GoldenCopy
            if n.startswith("v") and n[1:].isdigit() and ("v%d" % int(n[1:])) == n \
                    and os.path.isdir(os.path.join(root, n)):
                out.append(int(n[1:]))
    return sorted(out)


def world_files(root):
    out = {}
    for d, _, fs in os.walk(root):
        for f in fs:
            rel = os.path.relpath(os.path.join(d, f), root).replace("\\", "/")
            if rel in ("checksums.md5", "golden.json"):
                continue
            out[rel] = os.path.join(d, f)
    return out


def write_checksums(dst):
    files = world_files(dst)
    lines = ["%s  %s" % (V.md5_file(p).upper(), rel) for rel, p in sorted(files.items())]
    with open(os.path.join(dst, "checksums.md5"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    return files


def check(dst):
    """Точный состав + MD5 каждого файла по checksums.md5. Возвращает список проблем."""
    md5p = os.path.join(dst, "checksums.md5")
    if not os.path.isfile(md5p):
        return ["нет checksums.md5"]
    expected = {}
    with open(md5p, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip():
                continue
            parts = line.split("  ", 1)
            if len(parts) != 2:
                return ["битая строка: %r" % line]
            expected[parts[1].replace("\\", "/")] = parts[0]
    actual = world_files(dst)
    problems = []
    for rel in sorted(set(expected) - set(actual)):
        problems.append("нет файла: " + rel)
    for rel in sorted(set(actual) - set(expected)):
        problems.append("лишний файл: " + rel)
    for rel in sorted(set(expected) & set(actual)):
        if V.md5_file(actual[rel]).upper() != expected[rel].upper():
            problems.append("MD5 не совпал: " + rel)
    return problems


def atlas_spawn():
    with open(ATLAS, encoding="utf-8") as f:
        return tuple(int(v) for v in json.load(f)["spawn"])


def make(work, root):
    work = os.path.abspath(work)
    root = os.path.abspath(root)
    if not os.path.isfile(os.path.join(work, "level.dat")) or not os.path.isdir(os.path.join(work, "region")):
        raise SystemExit("это не мир TheZone (нет level.dat/region): " + work)
    vs = versions(root)
    n = (vs[-1] + 1) if vs else 1
    dst = os.path.join(root, "v%d" % n)
    if os.path.exists(dst):
        raise SystemExit("версия уже существует: %s — существующие эталоны не перезаписываются" % dst)
    os.makedirs(root, exist_ok=True)
    tmp = os.path.join(root, "v%d.tmp-%d" % (n, os.getpid()))
    if os.path.exists(tmp):
        raise SystemExit("временный каталог уже есть: " + tmp)

    data_dir = os.path.join(work, "data")

    def ignore(d, names):
        if os.path.samefile(d, work):
            return [x for x in names if x in EXCLUDE_DIRS or x in EXCLUDE_FILES]
        if os.path.isdir(data_dir) and os.path.samefile(d, data_dir):
            return [x for x in names if os.path.join("data", x) == SIM_SAVE]
        return []

    print("копирование рабочей копии (только чтение):", work)
    shutil.copytree(work, tmp, ignore=ignore)
    spawn = atlas_spawn()
    T.level_dat(tmp, spawn)  # Э1-08: команды, точка появления и игрок, spawnRadius 0, randomTickSpeed 0
    files = write_checksums(tmp)
    total = sum(os.path.getsize(p) for p in files.values())
    meta = {"version": n, "created": datetime.now().isoformat(timespec="seconds"),
            "source": work, "files": len(files), "bytes": total, "spawn": list(spawn),
            "tool": "golden_copy.py (Э1-11)"}
    with open(os.path.join(tmp, "golden.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(meta, f, ensure_ascii=False, indent=1)
    problems = check(tmp)
    if problems:
        shutil.rmtree(tmp, ignore_errors=True)
        raise SystemExit("сверка копии не прошла (%d проблем): %s" % (len(problems), "; ".join(problems[:5])))
    try:
        os.rename(tmp, dst)  # атомарно в пределах тома; повреждённая версия не становится эталоном
    except OSError:
        shutil.rmtree(tmp, ignore_errors=True)  # версия занята/том иной — эталон не создан, временное убрано
        raise
    print("готово: %s — файлов %d, %.1f МБ, точка появления %s" % (dst, len(files), total / 1048576.0, spawn))
    print("миры из копии создаёт кнопка «Зона» в меню создания мира (мод)")


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    cmd = sys.argv[1]
    if cmd == "make":
        if len(sys.argv) < 4:
            raise SystemExit(__doc__)
        make(sys.argv[2], sys.argv[3])
        return
    if cmd in ("verify", "list"):
        root = os.path.abspath(sys.argv[2])
        vs = [int(sys.argv[3])] if len(sys.argv) > 3 else versions(root)
        if not vs:
            print("золотых копий нет:", root)
            sys.exit(1 if cmd == "verify" else 0)
        bad = 0
        for n in vs:
            problems = check(os.path.join(root, "v%d" % n))
            if problems:
                bad += 1
                print("v%d: ПОВРЕЖДЕНА (%d): %s" % (n, len(problems), "; ".join(problems[:5])))
            else:
                print("v%d: целая" % n)
        sys.exit(1 if bad else 0)
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
