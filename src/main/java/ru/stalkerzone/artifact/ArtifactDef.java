package ru.stalkerzone.artifact;

import ru.stalkerzone.sim.AnomalyType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Каталог артефактов (v1.14, этап 4). У каждого есть плюс и почти у каждого — минус, как в ЗП и Anomaly: защита
 * тянет за собой радиацию, лечение — уязвимость к огню. Порядок первых восьми не менять: это прежние артефакты
 * (по одному на аномалию), их номера лежат в сохранениях. Новые добавлять только в конец.
 *
 * <p>Эффекты (за секунду на поясе): rad — доза радиации (минус — выводит), heal — лечение (минус — кровотечение,
 * раз в 5 с), carry — кг к переносимому весу, speed — доля скорости, stamina — выносливость (сытость),
 * therm/elec/chem/grav/psi — защита от аномалий своего рода и пси, armor — от любого урона (минус — уязвимость).
 */
public final class ArtifactDef {
    public final String id, ru, note;
    public final AnomalyType[] from;
    public final int tier, value;
    public final double rad, heal, carry, speed, stamina, therm, elec, chem, grav, psi, armor;
    /** Ведьмин студень: опасен вне фарфорового контейнера. */
    public final boolean caustic;

    private ArtifactDef(String id, String ru, AnomalyType[] from, int tier, int value, boolean caustic, String note, double... e) {
        this.id = id; this.ru = ru; this.from = from; this.tier = tier; this.value = value; this.caustic = caustic; this.note = note;
        rad = e[0]; heal = e[1]; carry = e[2]; speed = e[3]; stamina = e[4];
        therm = e[5]; elec = e[6]; chem = e[7]; grav = e[8]; psi = e[9]; armor = e[10];
    }

    private static AnomalyType[] t(AnomalyType... a) { return a; }

    private static final AnomalyType SPR = AnomalyType.SPRINGBOARD, WHI = AnomalyType.WHIRLIGIG, GRI = AnomalyType.GRINDER,
            MOS = AnomalyType.MOSQUITO, FRY = AnomalyType.FRY, FLU = AnomalyType.FLUFF, ELE = AnomalyType.ELECTRA, FOG = AnomalyType.FOG,
            COM = AnomalyType.COMET, JEL = AnomalyType.JELLY, RUS = AnomalyType.RUST, LIF = AnomalyType.LIFT, BRA = AnomalyType.BRAZIER,
            WEB = AnomalyType.WEB;

    //                                                                         rad    heal  carry speed  stam  therm  elec  chem  grav   psi  armor
    public static final ArtifactDef[] ALL = {
            // прежние восемь (номера 0..7)
            new ArtifactDef("kolobok", "Колобок", t(SPR, WHI), 3, 4, false, "Свернувшиеся иглы. Быстро затягивает раны.",
                    0.6, 1.0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            new ArtifactDef("stone_flower", "Каменный цветок", t(WHI, GRI), 2, 2, false, "Гасит пси-излучение.",
                    0.4, 0, 0, 0, 0, 0, 0, 0, 0, 0.2, 0),
            new ArtifactDef("stone_blood", "Каменная кровь", t(GRI, SPR), 1, 1, false, "Спрессованные останки. Слегка лечит.",
                    0.25, 0.4, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            new ArtifactDef("soul", "Душа", t(MOS, SPR), 3, 4, false, "Причудливый органический артефакт. Сильно лечит.",
                    0.5, 1.5, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            new ArtifactDef("fireball", "Огненный шар", t(FRY), 2, 2, false, "Выводит радиацию, но кожа горит легче.",
                    -0.6, 0, 0, 0, 0, -0.1, 0, 0, 0, 0, 0),
            new ArtifactDef("drop", "Капля", t(FLU, FRY), 1, 1, false, "Тёплая на ощупь. Прибавляет выносливости.",
                    0.2, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0),
            new ArtifactDef("flash", "Вспышка", t(ELE), 1, 1, false, "Держит заряд, защищает от разрядов.",
                    0.2, 0, 0, 0, 0.5, 0, 0.15, 0, 0, 0, 0),
            new ArtifactDef("thorn", "Колючка", t(FOG, JEL), 1, 1, false, "Выводит радиацию, но колет до крови.",
                    -0.4, -0.3, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            // новые
            new ArtifactDef("medusa", "Медуза", t(GRI, MOS, SPR), 1, 1, false, "Частично защищает от пуль и ударов.",
                    0.3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.05),
            new ArtifactDef("night_star", "Ночная звезда", t(WHI, MOS), 2, 2, false, "Ослабляет гравитацию вокруг владельца: ноша легче.",
                    0.5, 0, 6, 0, 0, 0, 0, 0, 0.1, 0, 0),
            new ArtifactDef("gravi", "Грави", t(LIF, MOS), 2, 2, false, "Тянет вверх. Груз кажется легче.",
                    0.4, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0),
            new ArtifactDef("goldfish", "Золотая рыбка", t(LIF, WHI), 3, 4, false, "Редкий гравитационный артефакт: несёшь, как пушинку.",
                    0.8, 0, 14, 0, 0, 0, 0, 0, 0, 0, 0),
            new ArtifactDef("crystal", "Кристалл", t(FRY, BRA), 2, 2, false, "Сильно выводит радиацию; жар переносится хуже.",
                    -1.0, 0, 0, 0, 0, -0.15, 0, 0, 0, 0, 0),
            new ArtifactDef("flame", "Пламя", t(COM, FRY), 2, 2, false, "Ускоряет заживление, но притягивает огонь.",
                    0, 0.8, 0, 0, 0, -0.15, 0, 0, 0, 0, 0),
            new ArtifactDef("eye", "Глаз", t(BRA, COM), 3, 3, false, "Похож на человеческий глаз. Быстро лечит, но огонь опаснее.",
                    0, 1.2, 0, 0, 0, -0.2, 0, 0, 0, 0, 0),
            new ArtifactDef("mama_beads", "Мамины бусы", t(COM, BRA, FLU), 3, 5, false, "Мечта любого сталкера: лечение и стойкость.",
                    0.5, 2.0, 0, 0, 0, 0, 0, 0, 0, 0, 0.05),
            new ArtifactDef("moonlight", "Лунный свет", t(ELE), 2, 3, false, "Мерцает голубым. Хорошо глушит пси-излучение.",
                    0, 0, 0, 0, 0, 0, -0.1, 0, 0, 0.3, 0),
            new ArtifactDef("battery", "Батарейка", t(ELE), 2, 2, false, "Бодрит: прибавляет скорости и выносливости.",
                    0.3, 0, 0, 0.06, 1, 0, 0.05, 0, 0, 0, 0),
            new ArtifactDef("empty", "Пустышка", t(ELE, WEB), 2, 3, false, "Два медных диска, а между ними — ничего. Пользы никакой, но институт платит.",
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            new ArtifactDef("etak", "Этак", t(ELE, LIF), 3, 4, false, "Вечный аккумулятор. Сил не убывает, ноги несут сами.",
                    0.5, 0, 0, 0.08, 2, 0, 0, 0, 0, 0, 0),
            new ArtifactDef("crystal_thorn", "Кристальная колючка", t(FOG, RUS), 2, 2, false, "Выводит радиацию, но раны кровоточат.",
                    -0.8, -0.4, 0, 0, 0, 0, 0, 0.05, 0, 0, 0),
            new ArtifactDef("slime", "Слизь", t(JEL, FOG), 1, 1, false, "Помогает от химических ожогов; немного лечит.",
                    0.1, 0.3, 0, 0, 0, 0, 0, 0.1, 0, 0, 0),
            new ArtifactDef("urchin", "Морской ёж", t(RUS, JEL), 3, 3, false, "Мощно выводит радиацию, но тело кровоточит.",
                    -1.5, -0.6, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            new ArtifactDef("bracelet", "Браслет", t(WEB), 2, 3, false, "Из «Пикника»: стимулирует жизненные процессы.",
                    0, 0.8, 0, 0, 0, 0, 0, 0, 0, 0.1, 0),
            new ArtifactDef("black_spray", "Чёрные брызги", t(WEB, GRI), 3, 4, false, "Свет в них идёт медленнее. Прочнее держит удар.",
                    0.4, 0, 0, 0, 0, 0, 0, 0, 0, 0.2, 0.1),
            new ArtifactDef("witch_jelly", "Ведьмин студень", t(JEL), 3, 8, true, "Коллоидный газ. Только в фарфоровом контейнере: всё остальное разъедает.",
                    2.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    };

    public static int index(String id) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].id.equals(id)) return i;
        return -1;
    }

    /** Защита от аномалии этого рода. */
    public double protection(AnomalyType t) {
        return switch (t) {
            case FRY, FLUFF, COMET, BRAZIER -> therm;
            case ELECTRA -> elec;
            case FOG, JELLY, RUST -> chem;
            case WEB -> 0;
            default -> grav;
        };
    }

    /**
     * Какой артефакт родила аномалия: из тех, что бывают в её роде; чем сильнее поле (уровень 0..3), тем выше
     * шанс на редкий. Ведьмин студень — только в сильном холодце.
     */
    public static int pick(AnomalyType type, int anomalyTier, Random r) {
        List<Integer> c = new ArrayList<>();
        List<Double> w = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < ALL.length; i++) {
            ArtifactDef d = ALL[i];
            boolean ok = false;
            for (AnomalyType a : d.from) if (a == type) ok = true;
            if (!ok || d.tier > anomalyTier + 1 || (d.caustic && anomalyTier < 2)) continue;
            double wt = switch (d.tier - anomalyTier) {
                case 1 -> 0.25;
                case 0 -> 1.0;
                default -> 0.8;
            };
            if (d.caustic) wt *= 0.3;
            c.add(i);
            w.add(wt);
            total += wt;
        }
        if (c.isEmpty()) {
            // у новых аномалий простых артефактов нет: в слабом поле родится самый простой из своих
            int best = -1;
            for (int i = 0; i < ALL.length; i++) {
                boolean ok = false;
                for (AnomalyType at : ALL[i].from) if (at == type) ok = true;
                if (ok && !ALL[i].caustic && (best < 0 || ALL[i].tier < ALL[best].tier)) best = i;
            }
            return best >= 0 ? best : Math.min(type.ordinal(), 7);
        }
        double x = r.nextDouble() * total;
        for (int i = 0; i < c.size(); i++) {
            x -= w.get(i);
            if (x <= 0) return c.get(i);
        }
        return c.get(c.size() - 1);
    }

    /** Описание эффектов строками (для подсказки предмета). */
    public List<String> effects() {
        List<String> out = new ArrayList<>();
        add(out, rad, "радиация", "§c+%.1f/с", "§a%.1f/с", true);
        add(out, heal, "здоровье", "§a+%.1f за 5 с", "§cкровотечение %.1f за 5 с", false);
        add(out, carry, "переносимый вес", "§a+%.0f кг", "§c%.0f кг", false);
        add(out, speed * 100, "скорость", "§a+%.0f%%", "§c%.0f%%", false);
        add(out, stamina, "выносливость", "§a+%.0f", "§c%.0f", false);
        add(out, therm * 100, "защита от огня", "§a+%.0f%%", "§c%.0f%%", false);
        add(out, elec * 100, "защита от электричества", "§a+%.0f%%", "§c%.0f%%", false);
        add(out, chem * 100, "защита от химии", "§a+%.0f%%", "§c%.0f%%", false);
        add(out, grav * 100, "защита от гравитации", "§a+%.0f%%", "§c%.0f%%", false);
        add(out, psi * 100, "пси-защита", "§a+%.0f%%", "§c%.0f%%", false);
        add(out, armor * 100, "защита от ранений", "§a+%.0f%%", "§c%.0f%%", false);
        return out;
    }

    private static void add(List<String> out, double v, String name, String plus, String minus, boolean inverse) {
        if (Math.abs(v) < 1e-9) return;
        boolean good = inverse ? v < 0 : v > 0;
        String fmt = v > 0 ? plus : minus;
        if (inverse) fmt = good ? "§a%.1f/с" : "§c+%.1f/с";
        out.add("§7" + name + ": " + String.format(fmt, v) + (good ? "" : ""));
    }
}
