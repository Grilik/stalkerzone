import java.nio.file.Files;
import java.nio.file.Path;

import ru.stalkerzone.economy.Prices;

/** Э8-09 (основа): таблица цен из Anomaly — шкала как в игре (аптечка — сотни, автомат — тысячи, артефакт — тысячи–десятки тысяч). */
public class PricesTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }
    static boolean in(int v, int lo, int hi) { return v >= lo && v <= hi; }

    public static void main(String[] a) throws Exception {
        Prices p = new Prices(Files.readString(Path.of("src/main/resources/data/stalkerzone/prices/prices.json")));
        ck(p.size() >= 80, "цен в таблице: " + p.size());
        ck(in(p.of("stalkerzone:good_meds"), 100, 999), "аптечка — сотни: " + p.of("stalkerzone:good_meds"));
        ck(in(p.of("stalkerzone:good_food"), 50, 300), "консервы — около сотни: " + p.of("stalkerzone:good_food"));
        ck(in(p.gun("szguns:pm"), 300, 2000) && p.gun("szguns:pm") < p.gun("szguns:toz_obrez"), "пистолет дешевле обреза");
        for (String g : new String[]{"szguns:ak74", "szguns:aks74u", "szguns:akm"}) ck(in(p.gun(g), 1000, 9999), g + " — тысячи: " + p.gun(g));
        ck(p.gun("szguns:gauss") > p.gun("szguns:svd") && p.gun("szguns:svd") > p.gun("szguns:ak74"), "гаусс > СВД > АК");
        for (String af : new String[]{"stone_blood", "drop", "flash", "goldfish", "witch_jelly"}) {
            int v = p.of("stalkerzone:artifact_" + af);
            ck(in(v, 1000, 99999), "артефакт " + af + " — тысячи–десятки тысяч: " + v);
        }
        ck(p.of("stalkerzone:suit_exo") > p.of("stalkerzone:suit_zarya") && p.of("stalkerzone:suit_zarya") > p.of("stalkerzone:suit_jacket"),
                "экзоскелет > «Заря» > куртка");
        ck(in(p.ammo("szguns:9x18"), 1, 20) && p.ammo("tacz:545x39") > p.ammo("szguns:9x18"), "патроны за штуку: 9×18 дешевле 5,45");
        ck(p.of("stalkerzone:ruble_100") == 100 && p.of("нет такого") == -1, "номинал рублей; неизвестный предмет — −1");
        System.exit(f > 0 ? 1 : 0);
    }
}
