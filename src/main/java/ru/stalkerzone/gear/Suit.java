package ru.stalkerzone.gear;

import ru.stalkerzone.sim.AnomalyType;
import ru.stalkerzone.sim.Good;

/**
 * Костюмы трёх происхождений (v1.12), как в оригинале у каждой группировки свои:
 * <ul>
 *   <li>{@link Origin#ZONE} — кустарные, шьют в мастерских группировок под условия Зоны: хорошо держат аномалии,
 *       у каждой фракции своя специализация (Долг — химия и радиация, Свобода — лёгкость и электричество,
 *       Монолит — пси, учёные — все аномалии);</li>
 *   <li>{@link Origin#PROTO} — универсальные прототипы техников учёных: чуть слабее лучших фракционных,
 *       зато без провалов; их скупают торговцы на перепродажу;</li>
 *   <li>{@link Origin#IMPORT} — с Большой земли: лучше держат пули, хуже аномалии, дороже.</li>
 * </ul>
 * Защита от аномалий в долях (0.5 = половина урона), она падает по мере износа. Порядок значений менять нельзя
 * только у первых пяти (старые предметы), новые можно дописывать.
 */
public enum Suit {
    //    id               название                                   происх.      фракция     броня креп. прочн. +вес  свой вес ед. терм  элек  хим  грав  скор.  пси
    JACKET("jacket",       "Кожаная куртка",                          Origin.ZONE,   "Одиночки", 3, 0f,   180,   0,   2.5,  1, 0.05, 0.05, 0.05, 0.05, 0,     0),
    ZARYA("zarya",         "Комбинезон «Заря»",                       Origin.ZONE,   "Одиночки", 5, 0f,   300,   5,   4.0,  2, 0.15, 0.15, 0.20, 0.10, 0,     0),
    ECOLOG("ecolog",       "Научный костюм ССП-99 «Эколог»",          Origin.ZONE,   "Учёные",   3, 0f,   260,   0,   5.0,  3, 0.55, 0.55, 0.65, 0.25, 0,     0.05),
    BULAT("bulat",         "Бронекостюм «Булат»",                     Origin.IMPORT, "Военные",  9, 2.5f, 480,   5,   9.0,  2, 0.08, 0.10, 0.08, 0.10, -0.04, 0),
    EXO("exo",             "Экзоскелет",                              Origin.ZONE,   "",         9, 3f,   600,  40,  20.0,  9, 0.20, 0.20, 0.20, 0.30, -0.12, 0),
    // --- v1.12: фракционные кустарные
    BANDIT("bandit",       "Куртка бандита",                          Origin.ZONE,   "Бандиты",  3, 0f,   200,   0,   3.0,  1, 0.08, 0.05, 0.08, 0.05, 0,     0),
    SEVA("seva",           "Комбинезон СЕВА",                         Origin.ZONE,   "Одиночки", 5, 0.5f, 320,   5,   5.5,  4, 0.45, 0.45, 0.55, 0.30, 0,     0.10),
    DUTY("duty",           "ПСЗ-9д «Броня Долга»",                    Origin.ZONE,   "Долг",     7, 1f,   400,   5,   7.0,  3, 0.15, 0.20, 0.35, 0.20, -0.02, 0),
    DUTY_HEAVY("duty_heavy", "ПСЗ-9Мд «Универсальная защита»",        Origin.ZONE,   "Долг",     8, 1.5f, 460,   5,   9.0,  5, 0.25, 0.30, 0.45, 0.25, -0.04, 0),
    FREEDOM("freedom",     "Комбинезон «Ветер свободы»",              Origin.ZONE,   "Свобода",  5, 0.5f, 320,   5,   4.5,  3, 0.25, 0.30, 0.15, 0.15, 0.02,  0),
    FREEDOM_GUARD("freedom_guard", "Бронекостюм «Страж свободы»",     Origin.ZONE,   "Свобода",  7, 1f,   400,   5,   7.0,  4, 0.30, 0.35, 0.20, 0.20, 0,     0),
    MONOLITH("monolith",   "Комбинезон Монолита",                     Origin.ZONE,   "Монолит",  7, 1f,   420,   5,   7.5,  4, 0.25, 0.25, 0.25, 0.25, -0.02, 0.35),
    ECOLOG_M("ecolog_m",   "Научный костюм ССП-99М",                  Origin.ZONE,   "Учёные",   4, 0f,   300,   0,   5.5,  4, 0.60, 0.60, 0.70, 0.35, 0,     0.10),
    // --- универсальные прототипы (учёные собирают, торговцы перепродают)
    PROTO_SCOUT("proto_scout", "Прототип «Следопыт»",                 Origin.PROTO,  "",         5, 0.5f, 330,   8,   4.5,  1, 0.30, 0.30, 0.30, 0.20, 0,     0.05),
    PROTO_GUARD("proto_guard", "Прототип «Гарант»",                   Origin.PROTO,  "",         7, 1f,   420,   5,   7.0,  2, 0.25, 0.25, 0.30, 0.25, -0.02, 0.05),
    PROTO_EXO("proto_exo", "Прототип экзоскелета «Атлант»",           Origin.PROTO,  "",         8, 2.5f, 560,  30,  17.0,  4, 0.20, 0.20, 0.25, 0.25, -0.08, 0.05),
    // --- с Большой земли
    BERILL("berill",       "Бронекомбинезон «Берилл-5М»",             Origin.IMPORT, "Военные",  7, 1.5f, 420,   5,   6.0,  1, 0.08, 0.08, 0.10, 0.08, 0,     0),
    SKAT("skat",           "Бронекостюм «Скат-9»",                    Origin.IMPORT, "Военные", 10, 3f,   560,  10,  12.0,  3, 0.10, 0.10, 0.12, 0.12, -0.06, 0),
    MERC("merc",           "Комплект наёмника (импорт)",              Origin.IMPORT, "",         6, 1f,   360,   5,   5.0,  1, 0.08, 0.08, 0.08, 0.06, 0.01,  0);

    /** Откуда снаряжение: определяет, каким товаром симуляции оно покупается и продаётся. */
    public enum Origin {
        ZONE("кустарное, сделано в Зоне", Good.GEAR),
        PROTO("универсальный прототип", Good.PROTO),
        IMPORT("с Большой земли", Good.GEAR_PRO);

        public final String ru;
        public final Good good;

        Origin(String ru, Good good) { this.ru = ru; this.good = good; }
    }

    public static final Suit[] ALL = values();

    public final String id, ru;
    public final Origin origin;
    /** Чья мастерская шьёт (пусто — общий). Фракционные модели лучших линеек продают только на земле своей группировки. */
    public final String faction;
    public final int defense;
    public final float toughness;
    public final int durability;
    public final double carryBonus, weight;
    /** Сколько единиц товара происхождения ({@link Origin#good}) стоит у торговца. */
    public final int gearUnits;
    public final double thermal, electric, chemical, gravity;
    /** Поправка скорости (тяжёлые костюмы медленнее). */
    public final double speed;
    /** Защита от пси-излучения (вдобавок к шлему). */
    public final double psi;

    Suit(String id, String ru, Origin origin, String faction, int defense, float toughness, int durability, double carryBonus,
         double weight, int gearUnits, double thermal, double electric, double chemical, double gravity, double speed, double psi) {
        this.id = id;
        this.ru = ru;
        this.origin = origin;
        this.faction = faction;
        this.defense = defense;
        this.toughness = toughness;
        this.durability = durability;
        this.carryBonus = carryBonus;
        this.weight = weight;
        this.gearUnits = gearUnits;
        this.thermal = thermal;
        this.electric = electric;
        this.chemical = chemical;
        this.gravity = gravity;
        this.speed = speed;
        this.psi = psi;
    }

    /** Защита нового костюма от аномалии этого типа. */
    public double protection(AnomalyType t) {
        return switch (t) {
            case FRY, FLUFF, COMET, BRAZIER -> thermal;
            case ELECTRA -> electric;
            case FOG, JELLY, RUST -> chemical;
            case WEB -> 0;
            default -> gravity;
        };
    }

    /** Тяжёлые костюмы (экзоскелеты) дают сопротивление отбрасыванию и бонус к весу только в исправном состоянии. */
    public boolean exo() { return this == EXO || this == PROTO_EXO; }

    /**
     * Лучшие фракционные линейки продаются только у торговца на земле своей группировки (Бар — Долг, Янтарь — учёные);
     * комбинезоны Монолита не продаются вовсе, их снимают с тел.
     */
    public boolean exclusive() {
        return this == DUTY_HEAVY || this == ECOLOG_M || this == MONOLITH || this == FREEDOM_GUARD;
    }

    /** Костюм по id или русскому названию-алиасу для /zone buy. */
    public String alias() {
        return switch (this) {
            case JACKET -> "куртка";
            case ZARYA -> "заря";
            case ECOLOG -> "эколог";
            case BULAT -> "булат";
            case EXO -> "экзоскелет";
            case BANDIT -> "бандитская";
            case SEVA -> "сева";
            case DUTY -> "броня-долга";
            case DUTY_HEAVY -> "универсальная-защита";
            case FREEDOM -> "ветер-свободы";
            case FREEDOM_GUARD -> "страж-свободы";
            case MONOLITH -> "монолит";
            case ECOLOG_M -> "эколог-м";
            case PROTO_SCOUT -> "следопыт";
            case PROTO_GUARD -> "гарант";
            case PROTO_EXO -> "атлант";
            case BERILL -> "берилл";
            case SKAT -> "скат";
            case MERC -> "наёмник";
        };
    }
}
