package ru.stalkerzone.gear;

import java.util.Map;

/**
 * Вес предметов TaCZ (П-10, плейтест 05.11: каждый патрон весил 3,5 кг). Чистая Java — проверяется тестом без игры.
 * Предметы TaCZ: {@code tacz:ammo} (NBT AmmoId), {@code tacz:ammo_box} (AmmoId + AmmoCount),
 * {@code tacz:modern_kinetic_gun} (GunId), {@code tacz:attachment} (AttachmentId).
 */
public final class TaczWeight {
    private TaczWeight() {}

    /** Патрон по калибру, кг за штуку (по массе реальных патронов, округлено). */
    private static final Map<String, Double> AMMO = Map.ofEntries(
            Map.entry("9x18", 0.010), Map.entry("9x19", 0.012), Map.entry("9mm", 0.012), Map.entry("9x39", 0.023),
            Map.entry("545x39", 0.011), Map.entry("556x45", 0.012), Map.entry("762x39", 0.016), Map.entry("762x25", 0.011),
            Map.entry("762x54", 0.022), Map.entry("308", 0.024), Map.entry("30_06", 0.026), Map.entry("792x57", 0.025),
            Map.entry("12g", 0.040), Map.entry("45acp", 0.021), Map.entry("357mag", 0.016), Map.entry("50ae", 0.030),
            Map.entry("500mag", 0.040), Map.entry("22wmr", 0.005), Map.entry("46x30", 0.007), Map.entry("57x28", 0.006),
            Map.entry("58x42", 0.013), Map.entry("68x51fury", 0.020), Map.entry("338", 0.045), Map.entry("45_70", 0.030),
            Map.entry("50bmg", 0.115), Map.entry("40mm", 0.230), Map.entry("rpg_rocket", 2.6), Map.entry("gauss", 0.050));

    /** Класс оружия известных стволов (TaCZ по умолчанию и наш пак szguns). */
    private static final Map<String, String> GUN_TYPE = Map.ofEntries(
            Map.entry("aa12", "shotgun"), Map.entry("ai_awp", "sniper"), Map.entry("ak47", "rifle"), Map.entry("aug", "rifle"),
            Map.entry("b93r", "pistol"), Map.entry("cz75", "pistol"), Map.entry("db_long", "shotgun"), Map.entry("db_short", "shotgun"),
            Map.entry("deagle", "pistol"), Map.entry("deagle_golden", "pistol"), Map.entry("fn_evolys", "mg"), Map.entry("fn_fal", "rifle"),
            Map.entry("g36k", "rifle"), Map.entry("glock_17", "pistol"), Map.entry("hk416d", "rifle"), Map.entry("hk_g3", "rifle"),
            Map.entry("hk_mk23", "pistol"), Map.entry("hk_mp5a5", "smg"), Map.entry("kar98", "sniper"), Map.entry("lonetrail", "pistol"),
            Map.entry("m1014", "shotgun"), Map.entry("m107", "sniper"), Map.entry("m16a1", "rifle"), Map.entry("m16a4", "rifle"),
            Map.entry("m1911", "pistol"), Map.entry("m249", "mg"), Map.entry("m320", "rpg"), Map.entry("m4a1", "rifle"),
            Map.entry("m700", "sniper"), Map.entry("m870", "shotgun"), Map.entry("m95", "sniper"), Map.entry("m9a4", "pistol"),
            Map.entry("minigun", "mg"), Map.entry("mk14", "rifle"), Map.entry("p320", "pistol"), Map.entry("p90", "smg"),
            Map.entry("qbz_191", "rifle"), Map.entry("qbz_95", "rifle"), Map.entry("rhino357", "pistol"), Map.entry("rpg7", "rpg"),
            Map.entry("rpk", "mg"), Map.entry("scar_h", "rifle"), Map.entry("scar_l", "rifle"), Map.entry("sks_tactical", "rifle"),
            Map.entry("spas_12", "shotgun"), Map.entry("spr15hb", "rifle"), Map.entry("springfield1873", "sniper"),
            Map.entry("taurus500", "pistol"), Map.entry("taurus943", "pistol"), Map.entry("timeless50", "pistol"),
            Map.entry("type_81", "rifle"), Map.entry("ump45", "smg"), Map.entry("uzi", "smg"), Map.entry("vector45", "smg"),
            // наш пак szguns
            Map.entry("ak74", "rifle"), Map.entry("akm", "rifle"), Map.entry("aks74", "rifle"), Map.entry("aks74u", "smg"),
            Map.entry("an94", "rifle"), Map.entry("aps", "pistol"), Map.entry("fort12", "pistol"), Map.entry("gauss", "sniper"),
            Map.entry("groza", "rifle"), Map.entry("pb", "pistol"), Map.entry("pkm", "mg"), Map.entry("pm", "pistol"),
            Map.entry("svd", "sniper"), Map.entry("toz34", "shotgun"), Map.entry("toz_obrez", "shotgun"), Map.entry("vss", "sniper"));

    /** Неизвестный калибр — как средний автоматный патрон. */
    public static final double AMMO_DEFAULT = 0.015;
    /** Пустая коробка для патронов. */
    public static final double BOX_EMPTY = 0.3;

    static String path(String id) {
        if (id == null) return "";
        int c = id.indexOf(':');
        return c < 0 ? id : id.substring(c + 1);
    }

    /** Один патрон калибра ammoId ("tacz:545x39", "szguns:9x18"). */
    public static double ammo(String ammoId) { return AMMO.getOrDefault(path(ammoId), AMMO_DEFAULT); }

    /** Класс оружия: pistol, smg, rifle, sniper, shotgun, mg, rpg; неизвестное — rifle. */
    public static String gunType(String gunId) { return GUN_TYPE.getOrDefault(path(gunId), "rifle"); }

    /** Оружие по классу, кг. */
    public static double gun(String gunId) {
        return switch (gunType(gunId)) {
            case "pistol" -> 1.0;
            case "smg" -> 3.0;
            case "rifle" -> 3.5;
            case "sniper" -> 4.5;
            case "shotgun" -> 3.5;
            case "mg" -> 8.0;
            case "rpg" -> 6.5;
            default -> 3.5;
        };
    }

    /** Обвес (прицел, глушитель, магазин, приклад…), кг. */
    public static double attachment(String attachmentId) {
        String p = path(attachmentId);
        if (p.startsWith("scope")) return 0.5;
        if (p.startsWith("muzzle") || p.contains("silencer") || p.contains("suppressor")) return 0.4;
        if (p.startsWith("extended_mag") || p.contains("mag")) return 0.3;
        if (p.startsWith("stock")) return 0.4;
        if (p.startsWith("sight")) return 0.2;
        return 0.3;
    }

    /** Коробка патронов: тара + патроны внутри. */
    public static double box(String ammoId, int count) { return BOX_EMPTY + Math.max(0, count) * ammo(ammoId); }

    /** Прочее TaCZ (верстаки, мишени и т. п.) — как блок. */
    public static final double OTHER = 0.8;

    /**
     * Вес одной штуки предмета TaCZ по пути id ("ammo", "modern_kinetic_gun"…) и строкам NBT.
     * nbtId — AmmoId / GunId / AttachmentId, boxCount — AmmoCount у коробки.
     */
    public static double unit(String itemPath, String nbtId, int boxCount) {
        return switch (itemPath) {
            case "ammo" -> ammo(nbtId);
            case "ammo_box" -> box(nbtId, boxCount);
            case "modern_kinetic_gun" -> gun(nbtId);
            case "attachment" -> attachment(nbtId);
            default -> OTHER;
        };
    }
}
