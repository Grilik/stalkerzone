import java.nio.file.Files;
import java.nio.file.Path;

/**
 * П-02в: забор «люками» не открывается. Блок — класс Minecraft, в чистой Java его не создать, поэтому проверка по
 * исходникам: секция забора регистрируется FixedTrapDoorBlock, а тот глушит клик, редстоун и ставится открытым.
 * Поведение в мире проверено на сервере (редстоун не меняет open/powered, обычный люк рядом — меняет).
 */
public class FenceTrapdoorTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    public static void main(String[] a) throws Exception {
        String reg = Files.readString(Path.of("src/main/java/ru/stalkerzone/block/ZoneBlocks.java")).replace("\r\n", "\n");
        String blk = Files.readString(Path.of("src/main/java/ru/stalkerzone/block/FixedTrapDoorBlock.java")).replace("\r\n", "\n");
        ck(reg.contains("m == ZoneMat.FENCE_BOARDS") && reg.contains("new FixedTrapDoorBlock("),
                "zm_fence_boards_trapdoor регистрируется неоткрываемым люком");
        ck(blk.contains("extends TrapDoorBlock"), "состояния и модели — как у люка");
        ck(blk.matches("(?s).*InteractionResult use\\(.*return InteractionResult\\.PASS;.*"), "клик рукой не открывает");
        ck(blk.matches("(?s).*void neighborChanged\\([^)]*\\)\\s*\\{\\s*}.*"), "редстоун и соседи не открывают");
        ck(blk.contains("setValue(OPEN, true)"), "ставится открытым (вертикальной доской)");
        System.exit(f > 0 ? 1 : 0);
    }
}
