package ru.stalkerzone.economy;

import ru.stalkerzone.zonemap.atlas.Json;

import java.util.HashMap;
import java.util.Map;

/**
 * Э8-09 (основа): базовые цены предметов, ₽, из {@code data/stalkerzone/prices/prices.json} (Anomaly cost × 0,2).
 * Ключи: id предмета ({@code stalkerzone:good_meds}), оружие TaCZ — {@code gun:<GunId>}, патроны — {@code ammo:<AmmoId>}
 * (цена за штуку). Одно место для добычи (Э1-17), торговли и наград (Э8-09). Без Minecraft — тестируется напрямую.
 */
public final class Prices {
    private final Map<String, Integer> price = new HashMap<>();

    public Prices(String json) {
        Map<String, Object> p = Json.obj(Json.obj(Json.parse(json)).get("prices"));
        for (Map.Entry<String, Object> e : p.entrySet()) price.put(e.getKey(), (int) Json.num(Json.obj(e.getValue()).get("price")));
    }

    /** Цена одной штуки или -1, если предмета в таблице нет. */
    public int of(String key) { return price.getOrDefault(key, -1); }

    public int gun(String gunId) { return of("gun:" + gunId); }

    public int ammo(String ammoId) { return of("ammo:" + ammoId); }

    public int size() { return price.size(); }
}
