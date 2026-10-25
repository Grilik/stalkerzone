package ru.stalkerzone.zonemap;

/** Элементы схемы локации. Все координаты в блоках мира, север это −Z. */
public final class Features {
    private Features() {}

    public enum PathType {
        ASPHALT("асфальт"), DIRT("грунтовка"), TRAIL("тропа"), RAIL("железная дорога");
        public final String ru;
        PathType(String ru) { this.ru = ru; }
    }

    /** Дорога, тропа или железная дорога (с насыпью). */
    public record Path(PathType type, double width, double embank, double[] pts) {
        public int n() { return pts.length / 2; }
    }

    /** Холм (или впадина при отрицательной высоте): гауссов купол. */
    public record Hill(double x, double z, double r, double h) {}

    /** Пруд или озеро-эллипс; глубина от уровня воды. */
    public record Pond(double x, double z, double rx, double rz, int depth) {}

    /** Ручей или река по линии. */
    public record River(double width, int depth, double[] pts) {}

    public enum ForestKind { MIXED, BIRCH, PINE, DEAD, RED, SWAMP_WILLOW }

    /** Лес: многоугольник, плотность деревьев 0..1, порода. */
    public record Forest(ForestKind kind, double density, double[] poly) {}

    public enum Ground { GRASS, FIELD, MUD, GRAVEL, ASH, PODZOL, CONCRETE, MARSH }

    /** Участок с особым покрытием земли (поле, бетонная площадка, болото...). */
    public record GroundArea(Ground ground, double[] poly) {}

    /** Объект: вид, точка, поворот (0, 90, 180, 270), выровненная площадка под ним. */
    public record Placement(String kind, int x, int z, int rot, int padW, int padD, long seed) {
        public Placement(String kind, int x, int z, int rot, int padW, int padD) {
            this(kind, x, z, rot, padW, padD, (long) x * 31 + z * 17L + kind.hashCode());
        }
    }

    public enum StType {
        BASE("база"), OUTPOST("аванпост"), CHECKPOINT("застава"), RESOURCE("ресурс (аномалии)"), LAIR("логово"), TERRITORY("территория"), LAB("лаборатория");
        public final String ru;
        StType(String ru) { this.ru = ru; }
    }

    /** Смарт-террен: точка, за которую воюют фракции (как в Call of Chernobyl). */
    public record Smart(String id, String name, StType type, int x, int z, int r, String owner, int capacity) {}
}
