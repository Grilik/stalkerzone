package ru.stalkerzone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.sim.ZoneScenario;

/** Состояние Зоны, хранящееся вместе с миром (data/stalkerzone_sim.dat). */
public class ZoneData extends SavedData {
    private static final String NAME = "stalkerzone_sim";

    public Simulation sim;
    /** Сколько тиков прошло с последнего игрового дня симуляции. */
    public int tickCounter;
    /** Локации, на которых уже построен лагерь. */
    public final java.util.Set<Integer> built = new java.util.HashSet<>();
    /** Высота земли в центре лагеря (после выравнивания площадки), по номеру локации. */
    public final java.util.Map<Integer, Integer> campY = new java.util.HashMap<>();
    /** Что сейчас нарисовано: ключ узел*16+тип постройки, значение (владелец+1, уровень, состояние). */
    public final java.util.Map<Integer, Integer> rendered = new java.util.HashMap<>();

    /** v1.14: спрятанные в аномалиях артефакты: x, y, z, тип (см. anomaly.Artifacts). */
    public final java.util.List<int[]> hidden = new java.util.ArrayList<>();
    /** v1.14: живая Зона — лента КПК, тайники погибших, слухи. */
    public ru.stalkerzone.sim.ZoneLife life = new ru.stalkerzone.sim.ZoneLife();

    private ZoneData(Simulation sim, int tickCounter) {
        this.sim = sim;
        this.tickCounter = tickCounter;
    }

    public static ZoneData get(MinecraftServer server) {
        long worldSeed = server.overworld().getSeed();
        return server.overworld().getDataStorage().computeIfAbsent(
                ZoneData::load,
                () -> new ZoneData(ZoneScenario.create(worldSeed), 0),
                NAME);
    }

    public static ZoneData load(CompoundTag tag) {
        // v1.11: состояние хранится байтами (строка NBT ограничена 65 535 байтами, а Зона выросла)
        String state = tag.contains("simBytes") ? java.util.Base64.getEncoder().encodeToString(tag.getByteArray("simBytes")) : tag.getString("sim");
        ZoneData d = new ZoneData(Simulation.load(state), tag.getInt("tick"));
        for (int id : tag.getIntArray("built")) d.built.add(id);
        int[] cy = tag.getIntArray("campY");
        for (int i = 0; i + 1 < cy.length; i += 2) d.campY.put(cy[i], cy[i + 1]);
        int[] rd = tag.getIntArray("rendered");
        for (int i = 0; i + 1 < rd.length; i += 2) d.rendered.put(rd[i], rd[i + 1]);
        int[] hd = tag.getIntArray("hidden");
        for (int i = 0; i + 3 < hd.length; i += 4) d.hidden.add(new int[] {hd[i], hd[i + 1], hd[i + 2], hd[i + 3]});
        if (tag.contains("life")) {
            try {
                d.life = ru.stalkerzone.sim.ZoneLife.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(tag.getByteArray("life"))));
            } catch (java.io.IOException e) {
                d.life = new ru.stalkerzone.sim.ZoneLife();
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putByteArray("simBytes", java.util.Base64.getDecoder().decode(sim.save()));
        tag.putInt("tick", tickCounter);
        tag.putIntArray("built", built.stream().mapToInt(Integer::intValue).toArray());
        tag.putIntArray("campY", flatten(campY));
        tag.putIntArray("rendered", flatten(rendered));
        int[] hd = new int[hidden.size() * 4];
        for (int i = 0; i < hidden.size(); i++) System.arraycopy(hidden.get(i), 0, hd, i * 4, 4);
        tag.putIntArray("hidden", hd);
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            life.write(new java.io.DataOutputStream(bo));
            tag.putByteArray("life", bo.toByteArray());
        } catch (java.io.IOException ignored) {
        }
        return tag;
    }

    private static int[] flatten(java.util.Map<Integer, Integer> m) {
        int[] out = new int[m.size() * 2];
        int i = 0;
        for (java.util.Map.Entry<Integer, Integer> e : m.entrySet()) {
            out[i++] = e.getKey();
            out[i++] = e.getValue();
        }
        return out;
    }

    public void reset(long seed) {
        sim = ZoneScenario.create(seed);
        life = new ru.stalkerzone.sim.ZoneLife();
        hidden.clear();
        tickCounter = 0;
        setDirty();
    }
}
