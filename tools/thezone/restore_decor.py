"""Э1-12: декор из Bedrock-оригинала TheZone в рабочую копию (через журнал Э1-03).

Что переносится (разбор старого формата сущностей, тег 0x32 LevelDB, и блок-сущностей 0x31):
- картины (19) — мотив, направление, место;
- жители (15) — профессия, имя автора; NoAI (у автора они стояли на месте: табличка «stop villagers moving»);
- 24 бирки (`nametag` Bedrock -> `minecraft:name_tag`) — Chunker не перенёс их в сундуки.
Не переносятся: животные, предметы на земле, падающие блоки, опыт (решение штаба); 395 стоек для брони —
пустые, без позы, все свалены в кучу у (−2843…−2836, 68…69, −525) — след командного цикла автора, не декор;
414 вагонеток с сундуками — пустые, у западного края данных (x ≈ −6360), за границей.

  python -I restore_decor.py <TheZone.mcworld> <рабочая копия> [--dry]
"""
import collections, math, os, struct, sys, uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil  # noqa: E402
import compare_worlds as cw  # noqa: E402
from anvil import BYTE, SHORT, INT, FLOAT, DOUBLE, STR, LIST, COMP, IARR  # noqa: E402
from world_edit_log import Work  # noqa: E402

DATA_VERSION = 3465  # Minecraft 1.20.1

# мотив Bedrock -> (id Java, ширина, высота) в блоках
PAINTINGS = {
    "Kebab": ("kebab", 1, 1), "Aztec": ("aztec", 1, 1), "Alban": ("alban", 1, 1), "Aztec2": ("aztec2", 1, 1),
    "Bomb": ("bomb", 1, 1), "Plant": ("plant", 1, 1), "Wasteland": ("wasteland", 1, 1),
    "Pool": ("pool", 2, 1), "Courbet": ("courbet", 2, 1), "Sea": ("sea", 2, 1), "Sunset": ("sunset", 2, 1),
    "Creebet": ("creebet", 2, 1), "Wanderer": ("wanderer", 1, 2), "Graham": ("graham", 1, 2),
    "Match": ("match", 2, 2), "Bust": ("bust", 2, 2), "Stage": ("stage", 2, 2), "Void": ("void", 2, 2),
    "SkullAndRoses": ("skull_and_roses", 2, 2), "Wither": ("wither", 2, 2), "Fighters": ("fighters", 4, 2),
    "Pointer": ("pointer", 4, 4), "Pigscene": ("pigscene", 4, 4), "BurningSkull": ("burning_skull", 4, 4),
    "Skeleton": ("skeleton", 4, 3), "DonkeyKong": ("donkey_kong", 4, 3),
}
# facing Java (2D): 0 юг, 1 запад, 2 север, 3 восток; нормаль и «против часовой» (Direction.getCounterClockWise)
NORMAL = {0: (0, 1), 1: (-1, 0), 2: (0, -1), 3: (1, 0)}
CCW = {0: (1, 0), 1: (0, 1), 2: (-1, 0), 3: (0, -1)}
PROFESSIONS = {"farmer", "librarian", "cleric", "armorer", "butcher", "nitwit", "shepherd", "fletcher",
               "weaponsmith", "toolsmith", "leatherworker", "mason", "cartographer", "fisherman"}


def roots(v):
    r = cw.NBT(v, True)
    out = []
    while r.o < len(v):
        out.append(r.root())
    return out


def uuid_ints(seed):
    u = uuid.uuid5(uuid.NAMESPACE_OID, "thezone-bedrock-%s" % seed).int
    return [struct.unpack(">i", struct.pack(">I", (u >> s) & 0xFFFFFFFF))[0] for s in (96, 64, 32, 0)]


def base(e, ident, pos, yaw):
    return {"id": (STR, ident),
            "Pos": (LIST, (DOUBLE, [float(p) for p in pos])),
            "Motion": (LIST, (DOUBLE, [0.0, 0.0, 0.0])),
            "Rotation": (LIST, (FLOAT, [float(yaw), 0.0])),
            "UUID": (IARR, uuid_ints(e.get("UniqueID"))),
            "FallDistance": (FLOAT, 0.0), "Fire": (SHORT, -1), "Air": (SHORT, 300), "OnGround": (BYTE, 0),
            "Invulnerable": (BYTE, 0), "PortalCooldown": (INT, 0)}


def painting(e):
    ident, w, h = PAINTINGS[e["Motive"]]
    f = int(e["Direction"]) % 4  # Bedrock Direction = Java facing (0 юг, 1 запад, 2 север, 3 восток); Rotation у Bedrock ненадёжен
    nx, nz = NORMAL[f]
    cx, cz = CCW[f]
    x, y, z = e["Pos"]
    ow, oh = (0.5 if w % 2 == 0 else 0.0), (0.5 if h % 2 == 0 else 0.0)
    tx = math.floor(x + nx * 0.46875 - ow * cx)
    tz = math.floor(z + nz * 0.46875 - ow * cz)
    ty = math.floor(y - oh)
    # сверка: у Bedrock Tile — блок стены за картиной, у Java — блок самой картины
    ok = (tx, ty, tz) == (e["TileX"] + nx, e["TileY"], e["TileZ"] + nz)
    d = base(e, "minecraft:painting", (x, y, z), f * 90.0)
    d.update({"variant": (STR, "minecraft:" + ident), "facing": (BYTE, f),
              "TileX": (INT, tx), "TileY": (INT, ty), "TileZ": (INT, tz)})
    return d, (tx, ty, tz), ok


def villager(e):
    prof = next((x[1:] for x in e.get("definitions", []) if x[1:] in PROFESSIONS), "none")
    d = base(e, "minecraft:villager", e["Pos"], e["Rotation"][0])
    d.update({"VillagerData": (COMP, {"profession": (STR, "minecraft:" + prof), "level": (INT, 1),
                                      "type": (STR, "minecraft:plains")}),
              "NoAI": (BYTE, 1), "PersistenceRequired": (BYTE, 1), "Health": (FLOAT, 20.0),
              "Offers": (COMP, {"Recipes": (LIST, (COMP, []))})})
    if e.get("CustomName"):
        d["CustomName"] = (STR, '{"text":"%s"}' % e["CustomName"].replace('"', ""))
    return d, prof


def main():
    mcworld, work, dry = sys.argv[1], sys.argv[2], "--dry" in sys.argv
    db = cw.bedrock_db(mcworld)
    ents, tags = [], []
    for k, v in db.items():
        ck = cw.bedrock_chunk_key(k)
        if not ck or ck[2] != 0:
            continue
        if ck[3] == 0x32:
            ents += roots(v)
        elif ck[3] == 0x31:
            for b in roots(v):
                if b.get("id") == "Chest":
                    for it in b.get("Items", []):
                        if it.get("Name") == "minecraft:nametag":
                            tags.append(((b["x"], b["y"], b["z"]), it["Slot"], it.get("Count", 1)))
    kinds = collections.Counter(e.get("definitions", ["?"])[0] for e in ents)
    print("сущностей в Bedrock:", len(ents), dict(kinds))

    by_chunk = collections.defaultdict(list)
    report = {"paintings": [], "villagers": [], "name_tags": []}
    for e in ents:
        kind = e.get("definitions", [""])[0]
        if kind == "+minecraft:painting":
            d, tile, ok = painting(e)
            by_chunk[(tile[0] >> 4, tile[2] >> 4)].append(d)
            report["paintings"].append((e["Motive"], tile, ok))
        elif kind == "+minecraft:villager":
            d, prof = villager(e)
            x, _, z = e["Pos"]
            by_chunk[(math.floor(x) >> 4, math.floor(z) >> 4)].append(d)
            report["villagers"].append((e.get("CustomName") or "", prof, [round(p, 1) for p in e["Pos"]]))

    for m, t, ok in report["paintings"]:
        print("картина", m, t, "сверка Tile ok" if ok else "СВЕРКА TILE НЕ СОШЛАСЬ")
    for n, p, pos in report["villagers"]:
        print("житель", n or "-", p, pos)
    print("бирок в сундуки:", len(tags))
    bad = [m for m, t, ok in report["paintings"] if not ok]
    if bad:
        sys.exit("Не сошлась сверка положения картин: %s — запись отменена." % bad)
    if dry:
        return

    w = Work(work)
    tag_chunks = collections.defaultdict(list)
    for pos, slot, cnt in tags:
        tag_chunks[(pos[0] >> 4, pos[2] >> 4)].append((pos, slot, cnt))
    files = sorted({"entities/" + anvil.region_name(*c) for c in by_chunk} |
                   {"region/" + anvil.region_name(*c) for c in tag_chunks})
    eid = w.begin("декор из Bedrock: %d картин, %d жителей, %d бирок" % (
        len(report["paintings"]), len(report["villagers"]), len(tags)),
        "Э1-12: Chunker не перенёс сущности старого формата и бирки", files)
    try:
        for (cx, cz), items in sorted(by_chunk.items()):
            p = w.p("entities/" + anvil.region_name(cx, cz))
            got = anvil.read_chunk(p, cx, cz) if os.path.exists(p) else None
            if got:
                name, ch = got
                ch.setdefault("Entities", (LIST, (COMP, [])))
                lst = ch["Entities"][1]
                if not lst[1]:
                    ch["Entities"] = (LIST, (COMP, []))
                    lst = ch["Entities"][1]
                have = {tuple(x["UUID"][1]) for x in lst[1] if "UUID" in x}
                lst[1].extend(x for x in items if tuple(x["UUID"][1]) not in have)
            else:
                name, ch = "", {"DataVersion": (INT, DATA_VERSION), "Position": (IARR, [cx, cz]),
                                "Entities": (LIST, (COMP, items))}
            anvil.write_chunk(p, cx, cz, name, ch)
        for (cx, cz), items in sorted(tag_chunks.items()):
            p = w.p("region/" + anvil.region_name(cx, cz))
            name, ch = anvil.read_chunk(p, cx, cz)
            bes = {(b["x"][1], b["y"][1], b["z"][1]): b for b in ch["block_entities"][1][1]}
            for pos, slot, cnt in items:
                be = bes[pos]
                inv = be.setdefault("Items", (LIST, (COMP, [])))[1]
                if not inv[1]:
                    be["Items"] = (LIST, (COMP, []))
                    inv = be["Items"][1]
                if any(i["Slot"][1] == slot for i in inv[1]):
                    raise RuntimeError("ячейка %d сундука %s занята" % (slot, pos))
                inv[1].append({"Slot": (BYTE, slot), "id": (STR, "minecraft:name_tag"), "Count": (BYTE, cnt)})
            anvil.write_chunk(p, cx, cz, name, ch)
    except Exception:
        w.undo(eid)
        raise
    w.commit(eid)
    print("записано, правка", eid, "файлов", len(files))


if __name__ == "__main__":
    main()
