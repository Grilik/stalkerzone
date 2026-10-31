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
    CONTROLLER("controller", "Контролёр", Model.HUMANOID,  0.7f, 1.9f, 55, 0.22, 4, 2,  25,  "controller_hand",     "Рука контролёра",          8, 0.7, 1.0f),
    // v1.14: новые (добавлять только в конец)
    PSEUDODOG("pseudodog", "Псевдособака", Model.QUAD,     0.9f, 1.0f, 26, 0.37, 5, 1,   8,  "pseudodog_tail",      "Хвост псевдособаки",       2, 0.4, 1.3f),
    PSYDOG("psydog",      "Пси-собака",   Model.QUAD,      0.9f, 1.0f, 34, 0.34, 5, 1,  14,  "psydog_tail",         "Хвост пси-собаки",         4, 0.4, 1.2f),
    TUSHKAN("tushkan",    "Тушкан",       Model.QUAD,      0.4f, 0.4f, 6,  0.40, 2, 0,   2,  "tushkan_paw",         "Лапа тушкана",             1, 0.1, 0.5f),
    BURER("burer",        "Бюрер",        Model.HUMANOID,  0.7f, 1.3f, 50, 0.24, 5, 3,  18,  "burer_hand",          "Рука бюрера",              5, 0.6, 0.72f),
    POLTERGEIST("poltergeist", "Полтергейст", Model.HUMANOID, 0.6f, 1.2f, 30, 0.28, 3, 0, 15, "poltergeist_core",    "Ядро полтергейста",        5, 0.3, 0.8f),
    PSEUDOGIANT("pseudogiant", "Псевдогигант", Model.PIG,  2.2f, 2.4f, 160, 0.21, 14, 8, 40, "pseudogiant_hand",    "Рука псевдогиганта",       9, 1.5, 2.6f);

    /** Вожак логова: пока он жив, логово выпускает свиту. */
    public boolean leader() { return this == CONTROLLER || this == PSEUDOGIANT || this == PSYDOG; }

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
            // стая слепых псов с псевдособаками; изредка её водит пси-собака
            case DOGS -> { double v = r.nextDouble(); yield v < 0.7 ? DOG : v < 0.93 || leaderPresent ? PSEUDODOG : PSYDOG; }
            case FLESH -> r.nextDouble() < 0.6 ? FLESH : BOAR;
            case BLOODSUCKERS -> r.nextDouble() < 0.7 ? BLOODSUCKER : SNORK;
            case CHIMERA -> r.nextDouble() < 0.5 ? CHIMERA : PSEUDODOG;
            case CONTROLLERS -> leaderPresent ? SNORK : CONTROLLER;
            case RODENTS -> TUSHKAN;
            case BURERS -> r.nextDouble() < 0.75 ? BURER : SNORK;
            case GIANTS -> leaderPresent ? (r.nextBoolean() ? FLESH : BOAR) : PSEUDOGIANT;
            case POLTERGEISTS -> POLTERGEIST;
        };
    }

    /**
     * Средняя «цена» мутанта логова: по ней считаем, сколько их показывать. Числа живут в
     * {@link ru.stalkerzone.sim.LairKind#mutantValue} — их читает и симуляция (счётчик целей, карточка 026),
     * чтобы мир и КПК не расходились.
     */
    public static double avgValue(ru.stalkerzone.sim.LairKind kind) {
        return kind.mutantValue;
    }
}
