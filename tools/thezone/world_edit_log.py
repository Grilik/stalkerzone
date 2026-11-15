"""Э1-03: журнал правок рабочей копии TheZone, резерв регионов и откат.

Правится только рабочая копия (в ней есть метка stalkerzone/WORKCOPY). Перед записью
затронутые файлы копируются в backup/<id>/, в журнал stalkerzone/edits.jsonl пишутся
что, где, зачем и md5 до/после. Откат undo возвращает файлы из резерва, только если
после этой правки их никто не менял. Мир при правке должен быть закрыт (игра, сервер).

  python -I world_edit_log.py init  <work> --original <папка оригинала>
  python -I world_edit_log.py begin <work> --what "..." --why "..." --files region/r.0.0.mca ...
  python -I world_edit_log.py commit <work> <id>
  python -I world_edit_log.py setblock <work> X Y Z minecraft:stone --why "..."
  python -I world_edit_log.py undo <work> <id>
  python -I world_edit_log.py log <work>
  python -I world_edit_log.py verify <work>      # файлы совпадают с журналом

Из Python: w = Work(path); eid = w.begin(...); ...правка...; w.commit(eid)
(при ошибке — w.undo(eid)).
"""
import argparse, datetime, hashlib, json, os, shutil, subprocess, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil  # noqa: E402

MARK = "stalkerzone/WORKCOPY"
LOG = "stalkerzone/edits.jsonl"


def md5(p):
    if not os.path.exists(p):
        return None
    h = hashlib.md5()
    with open(p, "rb") as f:
        while b := f.read(1 << 20):
            h.update(b)
    return h.hexdigest()


def now():
    return datetime.datetime.now().isoformat(timespec="seconds")


def git_sha():
    try:
        return subprocess.run(["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True,
                              cwd=os.path.dirname(os.path.abspath(__file__))).stdout.strip() or None
    except OSError:
        return None


class Work:
    def __init__(self, root):
        self.root = os.path.abspath(root)
        if not os.path.isfile(os.path.join(self.root, MARK)):
            sys.exit("Нет метки %s: это не рабочая копия. Оригинал и The Zone не правятся." % MARK)
        self.logp = os.path.join(self.root, LOG)

    def p(self, rel):
        return os.path.join(self.root, rel)

    def events(self):
        if not os.path.exists(self.logp):
            return []
        with open(self.logp, encoding="utf-8") as f:
            return [json.loads(l) for l in f if l.strip()]

    def append(self, ev):
        ev["time"] = now()
        with open(self.logp, "a", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(ev, ensure_ascii=False) + "\n")

    def edits(self):
        """id -> состояние правки (begin + последнее событие)."""
        st = {}
        for ev in self.events():
            if ev["op"] == "begin":
                st[ev["id"]] = dict(ev, status="open")
            elif ev["op"] in ("commit", "undo") and ev["id"] in st:
                st[ev["id"]]["status"] = "done" if ev["op"] == "commit" else "undone"
                if ev["op"] == "commit":
                    st[ev["id"]]["md5_after"] = ev["md5_after"]
        return st

    def begin(self, what, why, files, where=None):
        st = self.edits()
        if any(e["status"] == "open" for e in st.values()):
            sys.exit("Есть незавершённая правка: сначала commit или undo.")
        files = [f.replace("\\", "/") for f in files]
        eid = "e%04d" % (len(st) + 1)
        bdir = "backup/%s-%s" % (eid, datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
        before = {}
        for f in files:
            before[f] = md5(self.p(f))
            if before[f]:
                dst = self.p(os.path.join(bdir, f))
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(self.p(f), dst)
                if md5(dst) != before[f]:
                    sys.exit("Резерв %s не совпал — правка не начата." % f)
        self.append({"op": "begin", "id": eid, "what": what, "why": why, "where": where,
                     "files": files, "backup": bdir, "md5_before": before, "git": git_sha()})
        return eid

    def commit(self, eid):
        e = self.edits().get(eid)
        if not e or e["status"] != "open":
            sys.exit("Правка %s не открыта." % eid)
        self.append({"op": "commit", "id": eid, "md5_after": {f: md5(self.p(f)) for f in e["files"]}})

    def undo(self, eid):
        st = self.edits()
        e = st.get(eid)
        if not e or e["status"] == "undone":
            sys.exit("Правки %s нет или она уже отменена." % eid)
        later = [x["id"] for x in st.values() if x["id"] > eid and x["status"] != "undone"
                 and set(x["files"]) & set(e["files"])]
        if later:
            sys.exit("Отказ: те же файлы меняли позже правки %s — сначала отмените их." % ", ".join(later))
        if e["status"] == "done":
            changed = [f for f in e["files"] if md5(self.p(f)) != e["md5_after"][f]]
            if changed:
                sys.exit("Отказ: файлы изменены вне журнала после правки: %s" % ", ".join(changed))
        for f in e["files"]:
            if e["md5_before"][f] is None:
                if os.path.exists(self.p(f)):
                    os.remove(self.p(f))
            else:
                shutil.copy2(self.p(os.path.join(e["backup"], f)), self.p(f))
        bad = [f for f in e["files"] if md5(self.p(f)) != e["md5_before"][f]]
        if bad:
            sys.exit("Откат не сошёлся по md5: %s" % ", ".join(bad))
        self.append({"op": "undo", "id": eid})

    def verify(self):
        """Последнее известное по журналу md5 каждого файла против диска -> расхождения."""
        last, began = {}, {}
        for ev in self.events():  # по порядку событий: commit -> «после», undo -> «до»
            if ev["op"] == "begin":
                began[ev["id"]] = ev
            elif ev["op"] == "commit":
                last.update(ev["md5_after"])
            elif ev["op"] == "undo" and ev["id"] in began:
                last.update(began[ev["id"]]["md5_before"])
        return {f: (h, md5(self.p(f))) for f, h in last.items() if md5(self.p(f)) != h}


def setblock(w, x, y, z, block, why):
    cx, cz = x >> 4, z >> 4
    rel = "region/" + anvil.region_name(cx, cz)
    eid = w.begin("setblock %s" % block, why, [rel], where=[x, y, z])
    try:
        name, ch = anvil.read_chunk(w.p(rel), cx, cz)
        old = anvil.set_block(ch, x, y, z, block)
        anvil.write_chunk(w.p(rel), cx, cz, name, ch)
        got = anvil.get_block(anvil.read_chunk(w.p(rel), cx, cz)[1], x, y, z)
        assert got == anvil.state_str(anvil._state(block)), got
    except Exception:
        w.undo(eid)
        raise
    w.commit(eid)
    print("%s: (%d %d %d) %s -> %s" % (eid, x, y, z, old, block))
    return eid


def main():
    ap = argparse.ArgumentParser()
    sp = ap.add_subparsers(dest="cmd", required=True)
    a = sp.add_parser("init"); a.add_argument("work"); a.add_argument("--original", required=True)
    a = sp.add_parser("begin"); a.add_argument("work"); a.add_argument("--what", required=True)
    a.add_argument("--why", required=True); a.add_argument("--files", nargs="+", required=True)
    a = sp.add_parser("commit"); a.add_argument("work"); a.add_argument("id")
    a = sp.add_parser("undo"); a.add_argument("work"); a.add_argument("id")
    a = sp.add_parser("setblock"); a.add_argument("work")
    for k in "xyz":
        a.add_argument(k, type=int)
    a.add_argument("block"); a.add_argument("--why", required=True)
    a = sp.add_parser("log"); a.add_argument("work")
    a = sp.add_parser("verify"); a.add_argument("work")
    o = ap.parse_args()

    if o.cmd == "init":
        root = os.path.abspath(o.work)
        if os.path.abspath(o.original) == root:
            sys.exit("Рабочая копия не может быть оригиналом.")
        os.makedirs(os.path.join(root, "stalkerzone"), exist_ok=True)
        with open(os.path.join(root, MARK), "w", encoding="utf-8") as f:
            f.write("Рабочая копия TheZone. Оригинал: %s\n" % os.path.abspath(o.original))
        Work(root).append({"op": "init", "original": os.path.abspath(o.original)})
        print("ok")
        return
    w = Work(o.work)
    if o.cmd == "begin":
        print(w.begin(o.what, o.why, o.files))
    elif o.cmd == "commit":
        w.commit(o.id); print("ok")
    elif o.cmd == "undo":
        w.undo(o.id); print("откат", o.id, "ok")
    elif o.cmd == "setblock":
        setblock(w, o.x, o.y, o.z, o.block, o.why)
    elif o.cmd == "log":
        for e in sorted(w.edits().values(), key=lambda x: x["id"]):
            print(e["id"], e["status"], e["time"], e["what"], "|", e["why"], "|", ", ".join(e["files"]))
    elif o.cmd == "verify":
        bad = w.verify()
        print("ok" if not bad else "\n".join("%s: журнал %s, диск %s" % (f, a, b) for f, (a, b) in bad.items()))
        sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
