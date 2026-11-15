"""Э1-03: проверка резерва TheZone (только чтение).

Проверяет архивы (CRC всех файлов, md5 архива) и сверяет Java-архивы
с папкой мира: состав region/entities/level.dat, размеры и md5 каждого файла.
Пишет отчёт JSON. Ничего не изменяет.

python -I verify_backup.py <папка мира> <отчёт.json> <архив> [<архив> ...]
"""
import hashlib, json, os, sys, zipfile


def md5_file(p, buf=1 << 20):
    h = hashlib.md5()
    with open(p, "rb") as f:
        while b := f.read(buf):
            h.update(b)
    return h.hexdigest()


def md5_member(z, info, buf=1 << 20):
    h = hashlib.md5()
    with z.open(info) as f:
        while b := f.read(buf):
            h.update(b)
    return h.hexdigest()


def world_files(root):
    out = {}
    for d, _, fs in os.walk(root):
        for f in fs:
            p = os.path.join(d, f)
            out[os.path.relpath(p, root).replace("\\", "/")] = p
    return out


def strip_prefix(names):
    """Общий верхний каталог архива (например 'TheZone/') убирается."""
    tops = {n.split("/", 1)[0] for n in names if "/" in n}
    if len(tops) == 1 and all("/" in n for n in names):
        t = tops.pop() + "/"
        return {n: n[len(t):] for n in names}
    return {n: n for n in names}


def main():
    world, report, archives = sys.argv[1], sys.argv[2], sys.argv[3:]
    wf = world_files(world)
    wmd5 = {k: md5_file(p) for k, p in sorted(wf.items())}
    res = {"world": world, "world_files": len(wf),
           "world_bytes": sum(os.path.getsize(p) for p in wf.values()),
           "world_md5": wmd5, "archives": []}
    for a in archives:
        r = {"path": a, "bytes": os.path.getsize(a), "md5": md5_file(a)}
        with zipfile.ZipFile(a) as z:
            bad = z.testzip()
            r["crc_ok"] = bad is None
            r["first_bad"] = bad
            infos = [i for i in z.infolist() if not i.is_dir()]
            r["members"] = len(infos)
            names = strip_prefix([i.filename for i in infos])
            java = any(v.startswith("region/") for v in names.values())
            r["kind"] = "java" if java else "bedrock"
            if java:
                same, diff, missing = 0, [], []
                for i in infos:
                    rel = names[i.filename]
                    if rel not in wmd5:
                        missing.append(rel)
                        continue
                    if i.file_size == os.path.getsize(wf[rel]) and md5_member(z, i) == wmd5[rel]:
                        same += 1
                    else:
                        diff.append(rel)
                inzip = set(names.values())
                r.update(same=same, differ=diff[:50], differ_count=len(diff),
                         only_in_archive=missing[:50], only_in_archive_count=len(missing),
                         only_in_world=sorted(set(wmd5) - inzip)[:50],
                         only_in_world_count=len(set(wmd5) - inzip),
                         regions=sum(1 for v in inzip if v.startswith("region/")))
            else:
                r["db_files"] = sum(1 for v in names.values() if v.startswith("db/"))
        print(a, {k: v for k, v in r.items() if not isinstance(v, list)}, flush=True)
        res["archives"].append(r)
    with open(report, "w", encoding="utf-8") as f:
        json.dump(res, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
