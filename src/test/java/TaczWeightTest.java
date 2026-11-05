import ru.stalkerzone.gear.TaczWeight;

/** П-10: вес предметов TaCZ — патроны по калибру, коробки по содержимому, оружие по классу. */
public class TaczWeightTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    public static void main(String[] a) {
        double st = 60 * TaczWeight.unit("ammo", "tacz:545x39", 0);
        ck(st < 1.0, "стак 60 патронов 5,45 весит < 1 кг: " + st);
        ck(Math.abs(TaczWeight.ammo("szguns:9x18") - 0.010) < 1e-9, "9×18 ≈ 0,01");
        ck(Math.abs(TaczWeight.ammo("tacz:762x39") - 0.016) < 1e-9, "7,62×39 ≈ 0,016");
        ck(Math.abs(TaczWeight.ammo("tacz:12g") - 0.040) < 1e-9, "12/70 ≈ 0,04");
        ck(TaczWeight.ammo("кто-то:новый_калибр") == TaczWeight.AMMO_DEFAULT, "неизвестный калибр — по умолчанию");
        ck(TaczWeight.unit("modern_kinetic_gun", "szguns:pm", 0) == 1.0, "ПМ (пистолет) 1 кг");
        ck(TaczWeight.unit("modern_kinetic_gun", "szguns:aks74u", 0) == 3.0, "АКС-74У (ПП) 3 кг");
        ck(TaczWeight.unit("modern_kinetic_gun", "szguns:ak74", 0) == 3.5, "АК-74 3,5 кг");
        ck(TaczWeight.unit("modern_kinetic_gun", "szguns:svd", 0) == 4.5, "СВД 4,5 кг");
        ck(TaczWeight.unit("modern_kinetic_gun", "szguns:toz34", 0) == 3.5, "ТОЗ-34 3,5 кг");
        ck(TaczWeight.unit("modern_kinetic_gun", "szguns:pkm", 0) == 8.0, "ПКМ 8 кг");
        double box = TaczWeight.unit("ammo_box", "tacz:545x39", 120);
        ck(Math.abs(box - (TaczWeight.BOX_EMPTY + 120 * 0.011)) < 1e-9, "коробка 120 × 5,45 = тара + патроны: " + box);
        ck(TaczWeight.unit("ammo_box", "", 0) == TaczWeight.BOX_EMPTY, "пустая коробка");
        double att = TaczWeight.unit("attachment", "tacz:scope_acog_ta31", 0);
        ck(att >= 0.2 && att <= 0.5, "обвес 0,2–0,5: " + att);
        ck(TaczWeight.unit("gun_smith_table", "", 0) == TaczWeight.OTHER, "верстак — как блок");
        System.exit(f > 0 ? 1 : 0);
    }
}
