#!/usr/bin/env python3
"""Э8-09 (основа): таблица цен мода из конфигов Anomaly (поле cost) -> data/stalkerzone/prices/prices.json.

  python -I tools/economy/prices_from_anomaly.py <распакованные configs/items Anomaly>

Конфиги Anomaly — только локально (tools/xray/xdb.py extract ... configs/items/), в репозиторий не кладутся;
в репозитории — итоговые цены. Шкала: цена = cost Anomaly × SCALE (0,2): аптечка ≈ 500, автомат ≈ 5 000,
артефакт ≈ 4 000–20 000 — это «×8 к старым ценам мода» (медикаменты 60 → 480, артефакт 900 → 7 200), см. решение 16.11.
Патроны — за штуку (cost / box_size). Предметы без пары в Anomaly — старая цена мода × 8 (source "mod×8").
Добыча (Э1-17), торговля и награды (Э8-09) берут цены отсюда (ru.stalkerzone.economy.Prices).
"""
import json
import os
import re
import sys

SCALE = 0.2
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT = os.path.join(ROOT, "src/main/resources/data/stalkerzone/prices/prices.json")

# предмет мода -> раздел Anomaly (или ("mod", старая цена мода))
ITEMS = {
    "stalkerzone:good_food": "conserva", "stalkerzone:good_meds": "medkit", "stalkerzone:good_ammo": "ammo_9x18_fmj",
    "stalkerzone:good_ammo_pro": "ammo_5.45x39_fmj", "stalkerzone:good_parts": "prt_o_ballistic_2",
    "stalkerzone:good_materials": ("mod", 30), "stalkerzone:good_gear": ("mod", 350), "stalkerzone:good_gear_pro": "stalker_outfit",
    "stalkerzone:good_artifact": ("mod", 900), "stalkerzone:good_proto": ("mod", 560),
    "stalkerzone:bolt": "bolt", "stalkerzone:backpack_small": "itm_backpack",
    "stalkerzone:backpack_raid": ("x", "itm_backpack", 2.0), "stalkerzone:backpack_army": ("x", "itm_backpack", 3.0),
    "stalkerzone:detector_echo": "detector_simple", "stalkerzone:detector_bear": "detector_advanced", "stalkerzone:detector_veles": "detector_elite",
    "stalkerzone:suit_jacket": "novice_outfit", "stalkerzone:suit_bandit": "bandit_novice_outfit", "stalkerzone:suit_zarya": "stalker_outfit",
    "stalkerzone:suit_ecolog": "ecolog_outfit_white", "stalkerzone:suit_ecolog_m": "ecolog_outfit_green", "stalkerzone:suit_seva": "scientific_outfit",
    "stalkerzone:suit_bulat": "military_outfit", "stalkerzone:suit_exo": "exo_outfit", "stalkerzone:suit_duty": "dolg_outfit",
    "stalkerzone:suit_duty_heavy": "dolg_heavy_outfit", "stalkerzone:suit_freedom": "svoboda_light_outfit",
    "stalkerzone:suit_freedom_guard": "svoboda_heavy_outfit", "stalkerzone:suit_monolith": "monolith_outfit",
    "stalkerzone:suit_proto_scout": "cs_light_outfit", "stalkerzone:suit_proto_guard": "cs_heavy_outfit",
    "stalkerzone:suit_proto_exo": "ecolog_proto_exo_outfit", "stalkerzone:suit_berill": "specops_outfit",
    "stalkerzone:suit_skat": "specops_dolg_outfit", "stalkerzone:suit_merc": "merc_outfit",
}
ARTIFACTS = {  # id мода -> кандидаты разделов Anomaly
    "kolobok": ["af_fuzz_kolobok"], "stone_flower": ["af_cristall_flower"], "stone_blood": ["af_blood"], "soul": ["af_soul"],
    "fireball": ["af_fireball"], "drop": ["af_drops"], "flash": ["af_electra_flash"], "thorn": ["af_rusty_thorn"],
    "medusa": ["af_medusa"], "night_star": ["af_night_star"], "gravi": ["af_gravi"], "goldfish": ["af_gold_fish"],
    "crystal": ["af_cristall"], "flame": ["af_fire"], "eye": ["af_eye"], "mama_beads": ["af_dummy_glassbeads"],
    "moonlight": ["af_electra_moonlight"], "battery": ["af_dummy_battery"], "empty": ["af_empty"], "etak": ["af_glass"],
    "crystal_thorn": ["af_rusty_kristall"], "slime": ["af_ameba_slime"], "urchin": ["af_rusty_sea"],
    "bracelet": ["af_bracelet"], "black_spray": ["af_black_spray"], "witch_jelly": ["af_ameba_mica"],
}
GUNS = {
    "szguns:pm": "wpn_pm", "szguns:fort12": "wpn_fort", "szguns:aps": "wpn_aps", "szguns:toz_obrez": "wpn_toz34_obrez",
    "szguns:toz34": "wpn_toz34", "szguns:aks74u": "wpn_ak74u", "szguns:ak74": "wpn_ak74", "szguns:aks74": "wpn_aks74",
    "szguns:akm": "wpn_akm", "szguns:an94": "wpn_abakan", "szguns:svd": "wpn_svd", "szguns:pkm": "wpn_pkm",
    "szguns:groza": "wpn_groza", "szguns:vss": "wpn_vintorez", "szguns:gauss": "wpn_gauss",
}
AMMO = {  # за штуку
    "szguns:9x18": "ammo_9x18_fmj", "tacz:9mm": "ammo_9x19_fmj", "tacz:12g": "ammo_12x70_buck", "tacz:545x39": "ammo_5.45x39_fmj",
    "tacz:762x39": "ammo_7.62x39_fmj", "tacz:762x54": "ammo_7.62x54_7h1", "szguns:9x39": "ammo_9x39_pab9",
}
FIXED = {"stalkerzone:ruble": 1, "stalkerzone:ruble_100": 100, "stalkerzone:ruble_1000": 1000}


def load(root):
    secs = {}
    for dp, _, fn in os.walk(root):
        for f in fn:
            if not f.endswith(".ltx"):
                continue
            cur = None
            for line in open(os.path.join(dp, f), encoding="cp1251", errors="replace"):
                line = line.split(";")[0].strip()
                m = re.match(r"^\[([^\]]+)\]\s*(?::\s*(.*))?$", line)
                if m:
                    cur = m.group(1).strip()
                    secs.setdefault(cur, {"_par": []})
                    par = [p.strip() for p in (m.group(2) or "").split(",") if p.strip()]
                    if par:
                        secs[cur]["_par"] = par
                elif cur and "=" in line:
                    k, v = line.split("=", 1)
                    secs[cur][k.strip()] = v.strip()
    return secs


def get(secs, s, k, d=0):
    if d > 20 or s not in secs:
        return None
    if k in secs[s]:
        return secs[s][k]
    for p in secs[s]["_par"]:
        v = get(secs, p, k, d + 1)
        if v is not None:
            return v
    return None


def rnd(v):
    return int(round(v)) if v < 100 else int(round(v / 10.0) * 10)


def main():
    secs = load(sys.argv[1])

    def cost(s):
        c = get(secs, s, "cost")
        return float(c) if c not in (None, "") and float(c) > 0 else None

    out, miss = {}, []
    for it, src in ITEMS.items():
        if isinstance(src, tuple) and src[0] == "mod":
            out[it] = {"price": rnd(src[1] * 8), "source": "mod×8"}
        elif isinstance(src, tuple):
            out[it] = {"price": rnd(cost(src[1]) * SCALE * src[2]), "source": "%s ×%g" % (src[1], src[2])}
        else:
            c = cost(src)
            if c is None:
                miss.append(src); continue
            box = get(secs, src, "box_size")
            out[it] = {"price": rnd(c * SCALE), "source": src + (" (коробка %s)" % box if box else "")}
    for a, cands in ARTIFACTS.items():
        for s in cands:
            if cost(s):
                out["stalkerzone:artifact_" + a] = {"price": rnd(cost(s) * SCALE), "source": s}; break
        else:
            miss.append("af:" + a)
            out["stalkerzone:artifact_" + a] = {"price": rnd(900 * 8), "source": "mod×8"}
    for g, s in GUNS.items():
        c = cost(s)
        if c is None:
            miss.append(s); continue
        out["gun:" + g] = {"price": rnd(c * SCALE), "source": s}
    for a, s in AMMO.items():
        c, box = cost(s), get(secs, s, "box_size")
        if c is None:
            miss.append(s); continue
        out["ammo:" + a] = {"price": max(1, round(c * SCALE / float(box or 1))), "source": "%s / %s шт." % (s, box), "per": "штука"}
    for k, v in FIXED.items():
        out[k] = {"price": v, "source": "номинал"}
    doc = {"note": "Э8-09 (основа): базовые цены предметов, ₽. Источник — cost из конфигов Anomaly × %g (шкала 16.11: аптечка — сотни, "
                   "автомат — тысячи, артефакт — тысячи–десятки тысяч); без пары в Anomaly — старая цена мода × 8. Патроны — за штуку. "
                   "Генератор — tools/economy/prices_from_anomaly.py (конфиги Anomaly локально). Читает ru.stalkerzone.economy.Prices." % SCALE,
           "scale": SCALE, "prices": dict(sorted(out.items()))}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("цен:", len(out), "; без пары в Anomaly:", miss)


if __name__ == "__main__":
    main()
