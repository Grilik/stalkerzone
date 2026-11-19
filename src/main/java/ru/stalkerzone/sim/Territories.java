package ru.stalkerzone.sim;

import ru.stalkerzone.zonemap.atlas.Atlas;
import ru.stalkerzone.zonemap.atlas.Json;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Э1-16: территории зон атласа — занятие группировкой, локальные задачи, природный запас.
 *
 * <p>Определения берутся из атласа ({@code kind: zone}), состояние живёт в симуляции и сохраняется
 * вместе с ней (секция v25). Сервер — источник истины: занятие, освобождение и задачу меняет только
 * симуляция, игровой код вызывает эти методы, а не правит поля напрямую.
 *
 * <p>Территории не узлы: набор узлов, гарнизоны, население и склады товаров не меняются.
 * Владение не создаёт людей, денег, оружия и товаров и не превращает территорию в базу
 * (стройка отложена, Э5-13; занятие идёт в существующих постройках TheZone).
 */
public final class Territories {
    public final List<Territory> list = new ArrayList<>();

    /** Территории из атласа: потенциал записан в зоне, особенности измерены съёмкой Э1-04 и атласом. */
    public static List<Territory> fromAtlas(Atlas a) {
        List<Territory> out = new ArrayList<>();
        for (Map<String, Object> o : a.objects()) {
            if (!"zone".equals(Json.str(o.get("kind")))) continue;
            String id = Atlas.id(o);
            TerritoryKind kind = TerritoryKind.byId(Json.str(o.get("type")));
            if (kind == null) throw new IllegalArgumentException(id + ": неизвестный type территории");
            Map<String, Object> pot = Json.obj(o.get("potential"));
            if (pot == null || pot.isEmpty()) throw new IllegalArgumentException(id + ": нет potential{}");
            Map<String, Double> potential = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : pot.entrySet()) {
                String key = e.getKey();
                if (TerritoryPotential.FORBIDDEN.contains(key))
                    throw new IllegalArgumentException(id + ": территория не даёт " + key);
                if (!TerritoryPotential.KEYS.contains(key))
                    throw new IllegalArgumentException(id + ": неизвестный ключ потенциала " + key);
                double v = Json.num(e.getValue());
                if (Double.isNaN(v) || v < 0 || v > 1)
                    throw new IllegalArgumentException(id + ": сила " + key + " вне 0..1");
                if (v > 0) potential.put(key, v);
            }
            out.add(new Territory(id, kind, Json.str(o.get("name")), potential));
        }
        return out;
    }

    /** Особенности зоны из атласа (измеренные величины, см. {@link TerritoryPotential.Features}). */
    public static TerritoryPotential.Features features(Map<String, Object> zone) {
        Map<String, Object> f = Json.obj(zone.get("features"));
        if (f == null) return TerritoryPotential.Features.EMPTY;
        return new TerritoryPotential.Features(
                Json.num(f.get("forest")),
                (int) Json.num(f.get("buildingArea")),
                (int) Json.num(f.get("undergroundArea")),
                (int) Json.num(f.get("roads")),
                (int) Json.num(f.get("lairs")),
                (int) Json.num(f.get("fields")));
    }

    public boolean isEmpty() { return list.isEmpty(); }

    public Territory byId(String id) {
        for (Territory t : list) if (t.id.equals(id)) return t;
        return null;
    }

    /** Занять определениями из атласа (новый мир или сохранение без секции территорий). */
    public void define(List<Territory> definitions) {
        list.clear();
        list.addAll(definitions);
    }

    /**
     * Занять свободную пригодную территорию группировкой. Чужую занятую территорию занять нельзя
     * (захват — война, Э7-04); территорию без потенциала занимать незачем.
     */
    public boolean occupy(String id, int faction, int day) {
        Territory t = byId(id);
        if (t == null || faction < 0 || t.occupied() || !t.useful()) return false;
        t.owner = faction;
        t.occupiedDay = day;
        t.task = null;
        return true;
    }

    /** Освободить территорию: принадлежность и локальная задача снимаются, запас зоны остаётся. */
    public boolean release(String id) {
        Territory t = byId(id);
        if (t == null || !t.occupied()) return false;
        t.owner = -1;
        t.task = null;
        t.occupiedDay = -1;
        return true;
    }

    /** Назначить локальную задачу по потенциалу территории; {@code null} — снять задачу. */
    public boolean assign(String id, TerritoryTask task) {
        Territory t = byId(id);
        if (t == null || !t.occupied()) return false;
        if (task != null && !t.suitable(task)) return false;
        t.task = task;
        return true;
    }

    /** Добыча ресурса зоны людьми по локальной задаче (см. {@link Territory#take}). */
    public double take(String id, TerritoryTask task, int workers, int want, int day) {
        Territory t = byId(id);
        return t == null ? 0 : t.take(task, workers, want, day);
    }

    /** День симуляции: только восстановление природного запаса, никакого производства. */
    public void daily(int day) {
        for (Territory t : list) t.daily(day);
    }

    // ------------------------------------------------------------------ сохранение

    public void write(DataOutputStream out) throws IOException {
        out.writeInt(list.size());
        for (Territory t : list) t.write(out);
    }

    public void read(DataInputStream in) throws IOException {
        list.clear();
        int n = in.readInt();
        for (int i = 0; i < n; i++) list.add(Territory.read(in));
    }
}
