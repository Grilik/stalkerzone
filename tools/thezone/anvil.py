"""Минимальное чтение и запись мира Java 1.20.1: NBT с типами, регионы .mca, блок в секции.

Только для рабочей копии TheZone (процедура Э1-03, docs/thezone/03-world-edits.md).
NBT хранится как (тип, значение): compound -> dict имя -> (тип, значение), list -> (тип элемента, [значения]).
"""
import gzip, io, math, os, struct, time, zlib

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BARR, STR, LIST, COMP, IARR, LARR = range(13)


class _R:
    def __init__(self, b):
        self.b, self.o = b, 0

    def take(self, n):
        v = self.b[self.o:self.o + n]
        self.o += n
        return v

    def un(self, f):
        return struct.unpack(">" + f, self.take(struct.calcsize(">" + f)))[0]

    def s(self):
        return self.take(self.un("H")).decode("utf-8", "surrogatepass")


def _read(r, t):
    if t == BYTE: return r.un("b")
    if t == SHORT: return r.un("h")
    if t == INT: return r.un("i")
    if t == LONG: return r.un("q")
    if t == FLOAT: return r.un("f")
    if t == DOUBLE: return r.un("d")
    if t == BARR: return bytearray(r.take(r.un("i")))
    if t == STR: return r.s()
    if t == LIST:
        et, n = r.un("b"), r.un("i")
        return (et, [_read(r, et) for _ in range(n)])
    if t == COMP:
        d = {}
        while (tt := r.un("b")) != END:
            name = r.s()
            d[name] = (tt, _read(r, tt))
        return d
    if t == IARR:
        n = r.un("i"); return list(struct.unpack(">%di" % n, r.take(4 * n)))
    if t == LARR:
        n = r.un("i"); return list(struct.unpack(">%dq" % n, r.take(8 * n)))
    raise ValueError("NBT тип %d" % t)


def _ws(w, s):
    b = s.encode("utf-8", "surrogatepass"); w.write(struct.pack(">H", len(b))); w.write(b)


def _write(w, t, v):
    if t == BYTE: w.write(struct.pack(">b", v))
    elif t == SHORT: w.write(struct.pack(">h", v))
    elif t == INT: w.write(struct.pack(">i", v))
    elif t == LONG: w.write(struct.pack(">q", v))
    elif t == FLOAT: w.write(struct.pack(">f", v))
    elif t == DOUBLE: w.write(struct.pack(">d", v))
    elif t == BARR: w.write(struct.pack(">i", len(v))); w.write(bytes(v))
    elif t == STR: _ws(w, v)
    elif t == LIST:
        et, items = v
        w.write(struct.pack(">bi", et if items else (et or END), len(items)))
        for x in items: _write(w, et, x)
    elif t == COMP:
        for name, (tt, x) in v.items():
            w.write(struct.pack(">b", tt)); _ws(w, name); _write(w, tt, x)
        w.write(b"\x00")
    elif t == IARR: w.write(struct.pack(">i%di" % len(v), len(v), *v))
    elif t == LARR: w.write(struct.pack(">i%dq" % len(v), len(v), *v))
    else: raise ValueError("NBT тип %d" % t)


def nbt_load(b):
    r = _R(b)
    t = r.un("b"); name = r.s()
    return name, _read(r, t)


def nbt_dump(name, comp):
    w = io.BytesIO(); w.write(struct.pack(">b", COMP)); _ws(w, name); _write(w, COMP, comp)
    return w.getvalue()


# ---------------- регионы ----------------
def region_name(cx, cz):
    return "r.%d.%d.mca" % (cx >> 5, cz >> 5)


def read_chunk(path, cx, cz):
    """-> (name, compound) или None."""
    with open(path, "rb") as f:
        hdr = f.read(8192)
        i = 4 * ((cx & 31) + (cz & 31) * 32)
        loc = struct.unpack(">I", hdr[i:i + 4])[0]
        if not loc:
            return None
        f.seek((loc >> 8) * 4096)
        n, comp = struct.unpack(">IB", f.read(5))
        raw = f.read(n - 1)
    if comp & 0x80:
        raise ValueError("чанк во внешнем файле .mcc — не поддерживается")
    data = zlib.decompress(raw) if comp == 2 else gzip.decompress(raw) if comp == 1 else raw if comp == 3 else None
    if data is None:
        raise ValueError("сжатие %d не поддерживается" % comp)
    return nbt_load(data)


def write_chunk(path, cx, cz, name, comp):
    """Записывает чанк (zlib). На своё место, если влезает, иначе в конец файла."""
    payload = zlib.compress(nbt_dump(name, comp))
    blob = struct.pack(">IB", len(payload) + 1, 2) + payload
    need = math.ceil(len(blob) / 4096)
    blob += b"\0" * (need * 4096 - len(blob))
    if not os.path.exists(path):  # новый файл региона (например, entities/) — пустой заголовок
        with open(path, "wb") as f:
            f.write(b"\0" * 8192)
    with open(path, "r+b") as f:
        hdr = bytearray(f.read(8192))
        i = 4 * ((cx & 31) + (cz & 31) * 32)
        loc = struct.unpack(">I", hdr[i:i + 4])[0]
        off, cnt = loc >> 8, loc & 0xFF
        if not loc or need > cnt:
            f.seek(0, 2)
            end = f.tell()
            off = math.ceil(end / 4096)
            f.write(b"\0" * (off * 4096 - end))
        if need > 255:
            raise ValueError("чанк больше 1 МБ")
        f.seek(off * 4096); f.write(blob)
        hdr[i:i + 4] = struct.pack(">I", (off << 8) | need)
        hdr[4096 + i:4096 + i + 4] = struct.pack(">I", int(time.time()))
        f.seek(0); f.write(hdr)


# ---------------- блоки в секции ----------------
def _bits(n):
    return max(4, (n - 1).bit_length())


def _unpack(data, bits):
    per, mask, out = 64 // bits, (1 << bits) - 1, []
    for v in data:
        v &= (1 << 64) - 1
        for k in range(per):
            out.append((v >> (k * bits)) & mask)
    return out[:4096]


def _pack(idx, bits):
    per, out = 64 // bits, []
    for s in range(0, 4096, per):
        v = 0
        for k, x in enumerate(idx[s:s + per]):
            v |= x << (k * bits)
        out.append(v - (1 << 64) if v >= 1 << 63 else v)
    return out


def _state(block):
    """'minecraft:oak_log[axis=y]' -> compound палитры."""
    name, props = block, {}
    if "[" in block:
        name, rest = block[:-1].split("[", 1)
        props = dict(p.split("=", 1) for p in rest.split(",") if p)
    d = {"Name": (STR, name)}
    if props:
        d["Properties"] = (COMP, {k: (STR, v) for k, v in props.items()})
    return d


def state_str(st):
    s = st["Name"][1]
    if "Properties" in st:
        s += "[" + ",".join("%s=%s" % (k, v[1]) for k, v in sorted(st["Properties"][1].items())) + "]"
    return s


def _section(chunk, y, create=False):
    secs = chunk["sections"][1][1]
    for sec in secs:
        if sec["Y"][1] == y >> 4:
            return sec
    if not create:
        return None
    if not -4 <= y >> 4 <= 19:
        raise ValueError("высота %d вне мира" % y)
    near = min(secs, key=lambda s: abs(s["Y"][1] - (y >> 4)))  # пустая секция не хранится: биомы — от соседней
    sec = {"Y": (BYTE, y >> 4),
           "block_states": (COMP, {"palette": (LIST, (COMP, [{"Name": (STR, "minecraft:air")}]))}),
           "biomes": near["biomes"]}
    secs.append(sec)
    secs.sort(key=lambda s: s["Y"][1])
    return sec


def get_block(chunk, x, y, z):
    sec = _section(chunk, y)
    if sec is None:
        return "minecraft:air"
    bs = sec["block_states"][1]
    pal = bs["palette"][1][1]
    if "data" not in bs:
        return state_str(pal[0])
    i = _unpack(bs["data"][1], _bits(len(pal)))[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]
    return state_str(pal[i])


def set_block(chunk, x, y, z, block):
    """Ставит блок в чанк (словарь compound). Возвращает прежний блок.
    Блок-сущность на этом месте удаляется; ставить блоки с блок-сущностью нельзя (не проверяется)."""
    bs = _section(chunk, y, create=True)["block_states"][1]
    pal = bs["palette"][1][1]
    idx = _unpack(bs["data"][1], _bits(len(pal))) if "data" in bs else [0] * 4096
    p = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
    old = state_str(pal[idx[p]])
    new = _state(block)
    keys = [state_str(s) for s in pal]
    key = state_str(new)
    if key not in keys:
        pal.append(new); keys.append(key)
    idx[p] = keys.index(key)
    used = sorted(set(idx))  # сжать палитру
    remap = {o: n for n, o in enumerate(used)}
    pal[:] = [pal[o] for o in used]
    idx = [remap[i] for i in idx]
    if len(pal) == 1:
        bs.pop("data", None)
    else:
        bs["data"] = (LARR, _pack(idx, _bits(len(pal))))
    if "block_entities" in chunk:
        et, items = chunk["block_entities"][1]
        items[:] = [b for b in items if (b["x"][1], b["y"][1], b["z"][1]) != (x, y, z)]
    return old


# ---------------- блок-сущности (Э2-16) ----------------
def block_entity(chunk, x, y, z):
    """Блок-сущность в точке (compound) или None."""
    if "block_entities" not in chunk:
        return None
    for b in chunk["block_entities"][1][1]:
        if (b["x"][1], b["y"][1], b["z"][1]) == (x, y, z):
            return b
    return None


def set_block_entity(chunk, x, y, z, be_id, data):
    """Кладёт блок-сущность be_id с полями data (compound без x/y/z/id) в точку; прежняя снимается.
    Блок ставить раньше через set_block (она снимает сущность на этом месте)."""
    if "block_entities" not in chunk or chunk["block_entities"][1][0] not in (COMP, END):
        chunk["block_entities"] = (LIST, (COMP, []))
    lst = chunk["block_entities"][1]
    if lst[0] == END:
        chunk["block_entities"] = (LIST, (COMP, list(lst[1])))
    items = chunk["block_entities"][1][1]
    items[:] = [b for b in items if (b["x"][1], b["y"][1], b["z"][1]) != (x, y, z)]
    be = {"id": (STR, be_id), "x": (INT, x), "y": (INT, y), "z": (INT, z), "keepPacked": (BYTE, 0)}
    be.update(data)
    items.append(be)
    return be


def framed_camo(camo):
    """Поля блок-сущности FramedBlocks 9.4 (framedblocks:framed_tile) с камуфляжем camo
    ('minecraft:oak_log[axis=y]'): camo{type:framedblocks:block,state{Name,Properties}}, glowing/intangible/reinforced 0.
    Тот же формат, что строка Framed.camoNbt в моде."""
    return {"camo": (COMP, {"type": (STR, "framedblocks:block"), "state": (COMP, _state(camo))}),
            "glowing": (BYTE, 0), "intangible": (BYTE, 0), "reinforced": (BYTE, 0)}


FRAMED_TILE = "framedblocks:framed_tile"


def set_framed(chunk, x, y, z, block, camo):
    """Framed-блок с камуфляжем: состояние + блок-сущность framed_tile. Возвращает прежний блок."""
    old = set_block(chunk, x, y, z, block)
    set_block_entity(chunk, x, y, z, FRAMED_TILE, framed_camo(camo))
    return old


def relight(chunk):
    """Пересчитать свет и карты высот чанка при загрузке: isLightOn=0, Heightmaps убраны (ChunkSerializer
    строит недостающие заново). После замены полных блоков тонкими старые свет и высоты неверны."""
    chunk["isLightOn"] = (BYTE, 0)
    chunk.pop("Heightmaps", None)
