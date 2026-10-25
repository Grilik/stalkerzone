package ru.stalkerzone.world;

import net.minecraft.core.BlockPos;
import ru.stalkerzone.sim.Lab;
import ru.stalkerzone.sim.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Планировка подземной лаборатории: сетка комнат на одном или двух этажах, вход с поверхности по шахте,
 * терминалы в дальних комнатах (на втором этаже для сложных лабораторий). Всё определяется номером лаборатории.
 */
public final class LabLayout {
    public static final int PITCH = 14, ROOM = 11, HEIGHT = 5;

    public record Room(int floor, int col, int row, int x0, int y, int z0, String kind) {
        public int cx() { return x0 + ROOM / 2; }
        public int cz() { return z0 + ROOM / 2; }
        public BlockPos center() { return new BlockPos(cx(), y, cz()); }
    }

    public final Lab lab;
    public final int ex, ez, surfaceY;
    public final int cols1, rows1, cols2, rows2;
    public final int y1, y2;
    public final List<Room> rooms = new ArrayList<>();
    public final List<BlockPos> terminals = new ArrayList<>();
    public final List<Room> terminalRooms = new ArrayList<>();
    /** Шахта с поверхности (в комнату 0,0 первого этажа) и между этажами. */
    public final Room entryRoom, stairRoom1, stairRoom2;

    public LabLayout(Lab lab, Node n, int surfaceY) {
        this.lab = lab;
        this.ex = n.x * ZoneBounds.SCALE + lab.dx;
        this.ez = n.z * ZoneBounds.SCALE + lab.dz;
        this.surfaceY = surfaceY;
        int[][] size = {{3, 2, 0, 0}, {3, 3, 0, 0}, {4, 3, 3, 3}, {4, 4, 4, 3}, {5, 4, 4, 4}};
        int[] sz = size[Math.max(0, Math.min(4, lab.tier - 1))];
        cols1 = sz[0]; rows1 = sz[1]; cols2 = sz[2]; rows2 = sz[3];
        y1 = surfaceY - 22;
        y2 = y1 - 12;
        Random r = new Random(lab.id * 9973L + 17);
        String[] kinds = {"lab", "office", "storage", "generator", "lab", "dorm", "tanks"};
        int ox = ex - ROOM / 2, oz = ez - ROOM / 2;
        for (int c = 0; c < cols1; c++) for (int w = 0; w < rows1; w++)
            rooms.add(new Room(1, c, w, ox + c * PITCH, y1, oz + w * PITCH, kinds[r.nextInt(kinds.length)]));
        for (int c = 0; c < cols2; c++) for (int w = 0; w < rows2; w++)
            rooms.add(new Room(2, c, w, ox + c * PITCH, y2, oz + w * PITCH, kinds[r.nextInt(kinds.length)]));
        entryRoom = room(1, 0, 0);
        int sc = Math.min(cols1, cols2) - 1, sr = Math.min(rows1, rows2) - 1;
        stairRoom1 = cols2 > 0 ? room(1, sc, sr) : null;
        stairRoom2 = cols2 > 0 ? room(2, sc, sr) : null;
        // терминалы: в самых дальних от входа комнатах, у сложных лабораторий на нижнем этаже
        List<Room> candidates = new ArrayList<>();
        for (Room rm : rooms) if (rm != entryRoom && rm != stairRoom1) candidates.add(rm);
        candidates.sort((a, b) -> Double.compare(score(b), score(a)));
        for (int i = 0; i < lab.parts && i < candidates.size(); i++) {
            Room rm = candidates.get(i);
            terminalRooms.add(rm);
            terminals.add(new BlockPos(rm.cx(), rm.y, rm.z0 + 1));
        }
    }

    private double score(Room rm) {
        double d = Math.hypot(rm.cx() - ex, rm.cz() - ez);
        return d + (rm.floor == 2 ? 60 : 0) + ((rm.col * 7 + rm.row * 3) % 5);
    }

    public Room room(int floor, int col, int row) {
        for (Room rm : rooms) if (rm.floor == floor && rm.col == col && rm.row == row) return rm;
        return null;
    }

    public int minX() { return ex - ROOM / 2 - 2; }
    public int minZ() { return ez - ROOM / 2 - 2; }
    public int maxX() { return ex - ROOM / 2 + Math.max(cols1, cols2) * PITCH + 2; }
    public int maxZ() { return ez - ROOM / 2 + Math.max(rows1, rows2) * PITCH + 2; }
    public int minY() { return (cols2 > 0 ? y2 : y1) - 2; }

    /** Точка внутри лаборатории (под землёй). */
    public boolean inside(double x, double y, double z) {
        return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ() && y >= minY() && y <= surfaceY - 3;
    }

    public int terminalIndex(BlockPos p) {
        for (int i = 0; i < terminals.size(); i++) if (terminals.get(i).equals(p)) return i;
        return -1;
    }
}
