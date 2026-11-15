#!/usr/bin/env python3
"""Э1-01, шаг 2: что потерялось при конверсии TheZone Bedrock -> Java (Chunker). Только чтение.

    python -I tools/thezone/compare_worlds.py <TheZone.mcworld> "<папка Java-мира>" <out.json> [--sample N]

Bedrock: LevelDB из архива .mcworld читается напрямую (SST-таблицы, сжатие zlib/raw-deflate) — без сторонних библиотек.
Java: регионы r.x.z.mca. Собирается:
  * блочные сущности (таблички с текстом, сундуки с числом предметов, командные блоки с командой) — весь мир;
  * сущности (рамки, стойки, жители, мобы) — весь мир;
  * выборка чанков: набор блоков по секциям в обоих мирах -> что заменено/пропущено.
"""
import io, json, os, re, struct, sys, zipfile, zlib, collections, random

# ---------------- NBT (BE — little endian, Java — big endian) ----------------
class NBT:
    def __init__(self, data, le):
        self.d, self.o, self.e = data, 0, "<" if le else ">"
    def u(self, f):
        v = struct.unpack_from(self.e + f, self.d, self.o); self.o += struct.calcsize(f); return v[0]
    def s(self):
        n = self.u("H"); v = self.d[self.o:self.o + n].decode("utf-8", "replace"); self.o += n; return v
    def p(self, t):
        if t == 1: return self.u("b")
        if t == 2: return self.u("h")
        if t == 3: return self.u("i")
        if t == 4: return self.u("q")
        if t == 5: return self.u("f")
        if t == 6: return self.u("d")
        if t == 7: n = self.u("i"); v = self.d[self.o:self.o + n]; self.o += n; return v
        if t == 8: return self.s()
        if t == 9:
            et = self.u("b"); n = self.u("i"); return [self.p(et) for _ in range(n)]
        if t == 10:
            r = {}
            while True:
                tt = self.u("b")
                if tt == 0: return r
                k = self.s(); r[k] = self.p(tt)
        if t == 11: n = self.u("i"); v = struct.unpack_from(self.e + f"{n}i", self.d, self.o); self.o += 4 * n; return list(v)
        if t == 12: n = self.u("i"); v = struct.unpack_from(self.e + f"{n}q", self.d, self.o); self.o += 8 * n; return list(v)
        raise ValueError(f"тег {t} @ {self.o}")
    def root(self):
        t = self.u("b"); self.s(); return self.p(t)
    def roots(self):
        out = []
        while self.o < len(self.d):
            out.append(self.root())
        return out

# ---------------- LevelDB ----------------
def varint(b, o):
    r = sh = 0
    while True:
        c = b[o]; o += 1; r |= (c & 0x7f) << sh; sh += 7
        if c < 0x80: return r, o

def decompress(raw, kind):
    if kind == 0: return raw
    if kind == 2: return zlib.decompress(raw)
    if kind == 4: return zlib.decompress(raw, -15)
    raise ValueError(f"сжатие {kind}")

def block_entries(b):
    n_restarts = struct.unpack_from("<I", b, len(b) - 4)[0]
    end = len(b) - 4 - 4 * n_restarts
    o, key = 0, b""
    while o < end:
        shared, o = varint(b, o); nonshared, o = varint(b, o); vlen, o = varint(b, o)
        key = key[:shared] + b[o:o + nonshared]; o += nonshared
        val = b[o:o + vlen]; o += vlen
        yield key, val

def read_block(data, off, size):
    raw = data[off:off + size]; kind = data[off + size]
    return decompress(raw, kind)

def sst_items(data):
    footer = data[-48:]
    o = 0
    _, o = varint(footer, o); _, o = varint(footer, o)          # metaindex
    ioff, o = varint(footer, o); isize, o = varint(footer, o)    # index
    for _, h in block_entries(read_block(data, ioff, isize)):
        p = 0; doff, p = varint(h, p); dsize, p = varint(h, p)
        for k, v in block_entries(read_block(data, doff, dsize)):
            yield k, v

def log_items(data):
    """Записи журнала LevelDB (последние записи, ещё не сброшенные в SST)."""
    o, buf, out = 0, b"", []
    while o + 7 <= len(data):
        _, ln, typ = struct.unpack_from("<IHB", data, o); o += 7
        if typ == 0 and ln == 0:
            o = (o // 32768 + 1) * 32768; continue
        frag = data[o:o + ln]; o += ln
        if typ in (1,): rec = frag; buf = b""
        elif typ == 2: buf = frag; continue
        elif typ == 3: buf += frag; continue
        elif typ == 4: rec = buf + frag; buf = b""
        else: break
        p = 12; cnt = struct.unpack_from("<I", rec, 8)[0]
        for _ in range(cnt):
            t = rec[p]; p += 1
            kl, p = varint(rec, p); k = rec[p:p + kl]; p += kl
            if t == 1:
                vl, p = varint(rec, p); v = rec[p:p + vl]; p += vl; out.append((k, v))
            else:
                out.append((k, None))
    return out

def internal_key(k):
    """Внутренний ключ LevelDB = пользовательский ключ + 8 байт (seq<<8 | type)."""
    return k[:-8], k[-8] if len(k) >= 8 else 1

def bedrock_db(mcworld):
    z = zipfile.ZipFile(mcworld)
    names = [n for n in z.namelist() if n.startswith("db/")]
    db = {}
    # SST по номеру файла: более новые перекрывают старые
    def num(n): m = re.search(r"(\d+)\.(ldb|log)$", n); return int(m.group(1)) if m else -1
    for n in sorted([n for n in names if n.endswith(".ldb")], key=num):
        for k, v in sst_items(z.read(n)):
            uk, typ = internal_key(k)
            if typ == 1: db[uk] = v
            else: db.pop(uk, None)
    for n in sorted([n for n in names if n.endswith(".log")], key=num):
        for k, v in log_items(z.read(n)):
            if v is None: db.pop(k, None)
            else: db[k] = v
    return db

# ---------------- Bedrock: чанки ----------------
def bedrock_chunk_key(k):
    """(x, z, dim, tag, extra) или None для не-чанковых ключей."""
    if len(k) in (9, 10):
        x, zc = struct.unpack_from("<ii", k); return x, zc, 0, k[8], k[9] if len(k) == 10 else None
    if len(k) in (13, 14):
        x, zc, dim = struct.unpack_from("<iii", k); return x, zc, dim, k[12], k[13] if len(k) == 14 else None
    return None

def bedrock_subchunk_names(v):
    """Имена блоков палитры подчанка (версии 8/9 формата), слой 0."""
    ver = v[0]
    if ver not in (8, 9): return None
    o = 3 if ver == 9 else 2
    bits = v[o] >> 1; o += 1
    if bits == 0:
        n_words = 0
    else:
        per = 32 // bits; n_words = (4096 + per - 1) // per
    o += 4 * n_words
    if bits:
        psize = struct.unpack_from("<i", v, o)[0]; o += 4
    else:
        psize = 1
    r = NBT(v, True); r.o = o
    names = []
    for _ in range(psize):
        c = r.root(); names.append(c.get("name", "?"))
    return names

# ---------------- Java: регионы ----------------
def java_chunks(region_dir, wanted=None):
    for fn in os.listdir(region_dir):
        m = re.match(r"r\.(-?\d+)\.(-?\d+)\.mca$", fn)
        if not m: continue
        rx, rz = int(m.group(1)), int(m.group(2))
        if wanted is not None and (rx, rz) not in wanted: continue
        with open(os.path.join(region_dir, fn), "rb") as f: d = f.read()
        if len(d) < 8192: continue
        for i in range(1024):
            off = struct.unpack_from(">I", d, 4 * i)[0]
            if off == 0: continue
            s = (off >> 8) * 4096
            ln = struct.unpack_from(">I", d, s)[0]; comp = d[s + 4]
            raw = d[s + 5:s + 4 + ln]
            try:
                nbt = zlib.decompress(raw) if comp == 2 else __import__("gzip").decompress(raw)
            except Exception:
                continue
            cx, cz = rx * 32 + i % 32, rz * 32 + i // 32
            if wanted is not None and (cx, cz) not in wanted[(rx, rz)]: continue
            yield cx, cz, NBT(nbt, False).root()

def text_of(v):
    if isinstance(v, str):
        try:
            j = json.loads(v)
            if isinstance(j, dict): return j.get("text", "") + "".join(text_of(json.dumps(e)) for e in j.get("extra", []))
            if isinstance(j, str): return j
        except Exception:
            return v
    return ""

def java_be_summary(be):
    i = be.get("id", "?").replace("minecraft:", "")
    if i in ("sign", "hanging_sign"):
        msgs = be.get("front_text", {}).get("messages", [])
        return i, " / ".join(t for t in (text_of(m) for m in msgs) if t.strip())
    if i == "command_block": return i, be.get("Command", "")
    if "Items" in be: return i, len(be["Items"])
    return i, None

def bedrock_be_summary(be):
    i = be.get("id", "?")
    if i in ("Sign", "HangingSign"):
        t = be.get("FrontText", {}).get("Text") if "FrontText" in be else be.get("Text", "")
        return "sign", (t or "").replace("\n", " / ")
    if i == "CommandBlock": return "command_block", be.get("Command", "")
    if "Items" in be: return i.lower(), len(be["Items"])
    return i.lower(), None

def main():
    mcworld, jworld, out = sys.argv[1], sys.argv[2], sys.argv[3]
    sample_n = int(sys.argv[sys.argv.index("--sample") + 1]) if "--sample" in sys.argv else 40
    print("читаю LevelDB…", flush=True)
    db = bedrock_db(mcworld)
    print(f"ключей {len(db)}", flush=True)
    b_be = collections.Counter(); b_ent = collections.Counter(); b_signs = []; b_cmds = []; b_chest_items = 0
    b_chunks = set(); b_sub = {}
    actor_keys = 0
    for k, v in db.items():
        if k.startswith(b"actorprefix"):
            actor_keys += 1
            try:
                e = NBT(v, True).root(); b_ent[e.get("identifier", "?").replace("minecraft:", "")] += 1
            except Exception:
                b_ent["<ошибка разбора>"] += 1
            continue
        ck = bedrock_chunk_key(k)
        if not ck or ck[2] != 0: continue
        x, zc, _, tag, extra = ck
        if tag == 0x2f and extra is not None:
            b_chunks.add((x, zc))
            b_sub[(x, zc, extra if extra < 128 else extra - 256)] = v
        elif tag == 0x31:
            try:
                for be in NBT(v, True).roots():
                    kind, info = bedrock_be_summary(be); b_be[kind] += 1
                    pos = (be.get("x"), be.get("y"), be.get("z"))
                    if kind == "sign" and info: b_signs.append((pos, info))
                    if kind == "command_block": b_cmds.append((pos, info))
                    if isinstance(info, int): b_chest_items += info
            except Exception:
                b_be["<ошибка разбора>"] += 1
        elif tag == 0x32:
            try:
                for e in NBT(v, True).roots(): b_ent[e.get("identifier", str(e.get("id", "?"))).replace("minecraft:", "")] += 1
            except Exception:
                b_ent["<ошибка разбора>"] += 1
    print(f"Bedrock: чанков {len(b_chunks)}, блочных сущностей {sum(b_be.values())}, сущностей {sum(b_ent.values())}", flush=True)

    # выборка: чанки с наибольшим числом блочных сущностей Bedrock не известна по позиции → берём чанки с табличками и случайные
    rnd = random.Random(1)
    sign_chunks = list({(p[0] >> 4, p[2] >> 4) for p, _ in b_signs if p[0] is not None})
    sample = set(rnd.sample(sign_chunks, min(len(sign_chunks), sample_n // 2)))
    sample |= set(rnd.sample(sorted(b_chunks), min(len(b_chunks), sample_n - len(sample))))
    wanted = collections.defaultdict(set)
    for cx, cz in sample: wanted[(cx >> 5, cz >> 5)].add((cx, cz))

    print("читаю Java-регионы…", flush=True)
    j_be = collections.Counter(); j_ent = collections.Counter(); j_signs = []; j_cmds = []; j_chest_items = 0
    j_sample = {}
    rd = os.path.join(jworld, "region")
    for cx, cz, ch in java_chunks(rd):
        for be in ch.get("block_entities", []):
            kind, info = java_be_summary(be); j_be[kind] += 1
            pos = (be.get("x"), be.get("y"), be.get("z"))
            if kind == "sign" and info: j_signs.append((pos, info))
            if kind == "command_block": j_cmds.append((pos, info))
            if isinstance(info, int): j_chest_items += info
        if (cx, cz) in sample:
            secs = {}
            for s in ch.get("sections", []):
                pal = s.get("block_states", {}).get("palette", [])
                secs[s.get("Y")] = sorted({p.get("Name", "?").replace("minecraft:", "") for p in pal})
            j_sample[(cx, cz)] = secs
    ed = os.path.join(jworld, "entities")
    if os.path.isdir(ed):
        for cx, cz, ch in java_chunks(ed):
            for e in ch.get("Entities", []): j_ent[e.get("id", "?").replace("minecraft:", "")] += 1

    # сравнение палитр в выборке
    repl = collections.Counter(); missing_b = collections.Counter(); only_j = collections.Counter()
    compared = 0
    for (cx, cz), secs in j_sample.items():
        for (x, zc, sy), v in b_sub.items():
            if (x, zc) != (cx, cz): continue
            bn = bedrock_subchunk_names(v)
            if bn is None: continue
            bset = {n.replace("minecraft:", "") for n in bn}
            jset = set(secs.get(sy, []))
            compared += 1
            for n in bset - jset: missing_b[n] += 1
            for n in jset - bset: only_j[n] += 1

    res = {
        "bedrock": {"chunks": len(b_chunks), "block_entities": b_be.most_common(), "entities": b_ent.most_common(),
                    "signs_with_text": len(b_signs), "command_blocks": len(b_cmds), "container_items": b_chest_items,
                    "actor_records": actor_keys, "signs_sample": b_signs[:40], "commands_sample": b_cmds[:30]},
        "java": {"block_entities": j_be.most_common(), "entities": j_ent.most_common(),
                 "signs_with_text": len(j_signs), "command_blocks": len(j_cmds), "container_items": j_chest_items,
                 "signs_sample": j_signs[:40], "commands_sample": j_cmds[:30]},
        "sample": {"chunks": sorted(sample), "subchunks_compared": compared,
                   "bedrock_names_absent_in_java_same_section": missing_b.most_common(80),
                   "java_names_absent_in_bedrock_same_section": only_j.most_common(80)},
    }
    with open(out, "w", encoding="utf-8") as f: json.dump(res, f, ensure_ascii=False, indent=1, default=str)
    print("готово:", out)

if __name__ == "__main__":
    main()
