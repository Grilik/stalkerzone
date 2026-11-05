import ru.stalkerzone.zonemap.build.Framed;
import ru.stalkerzone.zonemap.build.Framed.Shape;

/** П-04: формы FramedBlocks с камуфляжем и ванильный запасной вариант без мода. */
public class FramedTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    public static void main(String[] a) {
        String shifer = "stalkerzone:zm_shifer";
        // мод установлен: framed-блок + NBT камуфляжа FramedBlocks 9.4
        Framed.loaded = true;
        String s = Framed.slope(shifer, "north", false);
        ck(s.equals("framedblocks:framed_slope[facing=north,type=bottom]{camo:{type:\"framedblocks:block\",state:{Name:\"stalkerzone:zm_shifer\"}}}"),
                "скат из шифера: " + s);
        ck(Framed.of(Shape.PILLAR, "minecraft:oak_log[axis=y]", "y", false)
                .equals("framedblocks:framed_pillar[axis=y]{camo:{type:\"framedblocks:block\",state:{Name:\"minecraft:oak_log\",Properties:{axis:\"y\"}}}}"),
                "камуфляж с состоянием (свойства в Properties)");
        ck(Framed.slab(shifer, true).startsWith("framedblocks:framed_slab[top=true]{"), "плита сверху");
        ck(Framed.of(Shape.CUBE, shifer, "north", false).startsWith("framedblocks:framed_cube{camo:"), "куб без свойств");
        String[] sp = Framed.split(s);
        ck(sp[0].equals("framedblocks:framed_slope[facing=north,type=bottom]") && sp[1].startsWith("{camo:"), "split: состояние и NBT");
        ck(Framed.split("minecraft:stone")[1].isEmpty(), "split без NBT");
        // фигурные скобки сбалансированы — TagParser прочитает
        int depth = 0; boolean ok = true;
        for (char c : sp[1].toCharArray()) { if (c == '{') depth++; if (c == '}') depth--; if (depth < 0) ok = false; }
        ck(ok && depth == 0, "NBT сбалансирован");

        // мода нет: ступени/плиты того же материала, без NBT
        Framed.loaded = false;
        for (Shape sh : Shape.values()) ck(!Framed.of(sh, shifer, "east", false).contains("{"), "без мода нет NBT: " + sh);
        ck(Framed.slope(shifer, "east", true).equals("stalkerzone:zm_shifer_stairs[facing=east,half=top,shape=straight]"), "скат → ступени zm_*");
        ck(Framed.cornerSlope("minecraft:oak_planks", "south", false).equals("minecraft:oak_stairs[facing=south,half=bottom,shape=outer_left]"), "угол → ступени outer (дуб)");
        ck(Framed.innerCornerSlope("minecraft:bricks", "west", false).equals("minecraft:brick_stairs[facing=west,half=bottom,shape=inner_left]"), "внутренний угол → inner (кирпич)");
        ck(Framed.slab("minecraft:stone_bricks", false).equals("minecraft:stone_brick_slab[type=bottom]"), "плита → плита");
        ck(Framed.slope("minecraft:iron_block", "north", false).equals("minecraft:iron_block"), "нет ступеней у материала → полный блок");
        ck(Framed.panel(shifer, "north").equals(shifer), "панель → полный блок");
        ck(Framed.of(Shape.WALL_BOARD, shifer, "north", false).equals("minecraft:air"), "доска на стену → ничего");
        ck(Framed.of(Shape.PILLAR, "minecraft:oak_log[axis=y]", "y", false).equals("minecraft:oak_log[axis=y]"), "столб → сам блок с состоянием");

        // зависание 05.11: BlockEntity.setChanged() при загрузке чанка запрашивает этот же чанк у сервера
        try {
            String zp = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/ru/stalkerzone/world/ZoneMapPlacer.java"));
            int a0 = zp.indexOf("public static void applyNbt"), a1 = zp.indexOf("\n    }\n", a0);
            String body = zp.substring(a0, a1);
            ck(!body.replaceAll("//.*", "").contains("setChanged"), "applyNbt не зовёт setChanged (иначе зависание при загрузке чанка)");
        } catch (java.io.IOException e) { ck(false, "нет ZoneMapPlacer.java: " + e); }
        System.exit(f > 0 ? 1 : 0);
    }
}
