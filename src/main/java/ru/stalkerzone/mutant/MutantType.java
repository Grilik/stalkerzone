package ru.stalkerzone.mutant;

/**
 * Виды мутантов в мире. value: сколько силы логова стоит один мутант (убийство ослабляет логово на столько),
 * part: что с него можно срезать на продажу (продаётся как сырьё «Медикаменты» у торговца).
 */
public enum MutantType {
    //     id             имя             модель            шир  выс  здор скор  урон броня ценн  часть                  название части              ед. вес
    DOG("blind_dog",      "Слепой пёс",   Model.QUAD,      0.7f, 0.8f, 12, 0.36, 3, 0,   5,  "dog_tail",            "Хвост слепого пса",        1, 0.3, 1.0f),
    FLESH("flesh",        "Плоть",        Model.PIG,       0.9f, 0.9f, 22, 0.24, 4, 2,   6,  "flesh_eye",           "Глаз плоти",               1, 0.2, 1.1f),
    BOAR("boar",          "Кабан",        Model.PIG,       1.1f, 1.1f, 30, 0.30, 6, 4,   8,  "boar_hoof",           "Копыто кабана",            2, 0.6, 1.35f),
    SNORK("snork",        "Снорк",        Model.HUMANOID,  0.6f, 1.6f, 22, 0.33, 5, 2,   7,  "snork_foot",          "Ступня снорка",            2, 0.5, 0.85f),
    BLOODSUCKER("bloodsucker", "Кровосос", Model.HUMANOID, 0.7f, 2.0f, 42, 0.30, 8, 4,  12,  "bloodsucker_tentacle", "Щупальца кровососа",       3, 0.4, 1.05f),
    CHIMERA("chimera",    "Химера",       Model.QUAD,      1.3f, 1.4f, 70, 0.38, 11, 6, 20,  "chimera_claw",        "Коготь химеры",            6, 0.8, 1.9f),
    CONTROLLER("controller", "Контролёр", Model.HUMANOID,  0.7f, 1.9f, 55, 0.22, 4, 2,  25,  "controller_hand",     "Рука контролёра",          8, 0.7, 1.0f);

    public enum Model { QUAD, PIG, HUMANOID }

    public static final MutantType[] ALL = values();

    public final String id, ru;
    public final Model model;
    public final float width, height;
    public final double health, speed, damage, armor, value;
    public final String partId, partRu;
    public final int partUnits;
    public final double partWeight;
    public final float scale;

    MutantType(String id, String ru, Model model, float width, float height, double health, double speed, double damage, double armor,
               double value, String partId, String partRu, int partUnits, double partWeight, float scale) {
        this.id = id;
        this.ru = ru;
        this.model = model;
        this.width = width;
        this.height = height;
        this.health = health;
        this.speed = speed;
        this.damage = damage;
        this.armor = armor;
        this.value = value;
        this.partId = partId;
        this.partRu = partRu;
        this.partUnits = partUnits;
        this.partWeight = partWeight;
        this.scale = scale;
    }

    /** Кого выпускает логово данного вида. */
    public static MutantType pick(ru.stalkerzone.sim.LairKind kind, boolean leaderPresent, java.util.Random r) {
        return switch (kind) {
            case DOGS -> DOG;
            case FLESH -> r.nextDouble() < 0.6 ? FLESH : BOAR;
            case BLOODSUCKERS -> r.nextDouble() < 0.7 ? BLOODSUCKER : SNORK;
            case CHIMERA -> r.nextDouble() < 0.5 ? CHIMERA : DOG;
            case CONTROLLERS -> leaderPresent ? SNORK : CONTROLLER;
        };
    }

    /** Средняя «цена» мутанта логова: по ней считаем, сколько их показывать. */
    public static double avgValue(ru.stalkerzone.sim.LairKind kind) {
        return switch (kind) {
            case DOGS -> DOG.value;
            case FLESH -> FLESH.value * 0.6 + BOAR.value * 0.4;
            case BLOODSUCKERS -> BLOODSUCKER.value * 0.7 + SNORK.value * 0.3;
            case CHIMERA -> (CHIMERA.value + DOG.value) / 2;
            case CONTROLLERS -> SNORK.value * 1.5;
        };
    }
}
