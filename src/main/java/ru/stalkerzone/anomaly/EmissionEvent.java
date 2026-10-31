package ru.stalkerzone.anomaly;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import ru.stalkerzone.StalkerZoneMod;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.mutant.MutantEntity;
import ru.stalkerzone.mutant.MutantRegistry;
import ru.stalkerzone.mutant.MutantType;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.NodeType;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.SpawnFinder;
import ru.stalkerzone.world.ZoneBounds;

import java.util.Random;

/**
 * Выброс v2 (v1.14, этап 3) — главное событие Зоны, как в книгах и ЗП:
 * <ol>
 *   <li>Примета: за 2 минуты аномалии затихают, воют собаки, по рации — «нездоровое небо на севере».</li>
 *   <li>Тревога в КПК за минуту: где ближайшее укрытие; за 20 секунд — сирена и обратный отсчёт.</li>
 *   <li>Сам выброс: гроза, сухие молнии, пульсирующая темнота, урон под открытым небом растёт, финальная волна
 *       смертельна для оставшихся снаружи.</li>
 *   <li>Гон: сразу после — стаи мутантов бегут с севера на юг.</li>
 *   <li>«Рыбалка после дождя»: до конца суток свежие артефакты мерцают и находятся без детектора.</li>
 * </ol>
 * Активность мутантов после выброса — по кривой {@link #mutantActivity}: затишье, потом пик на 3–4-е сутки.
 */
public final class EmissionEvent {
    private static final int DURATION = 1200;
    /** За сколько тиков до выброса начинаются приметы, тревога в КПК и сирена. */
    public static final int OMEN = 2400, ALARM = 1200, SIREN = 400;
    private static final int GON = 1400;
    private static final String GON_TAG = "sz_gon";
    private static final Random RND = new Random();

    private static int ticksLeft;
    private static int gonLeft;
    /** Выброс, назначенный командой (/zone emission soon): тиков до него, со всеми приметами. */
    private static int scheduled;

    public static void schedule(int ticks) { scheduled = ticks; }

    private EmissionEvent() {}

    public static boolean active() { return ticksLeft > 0; }

    /** Через сколько тиков выброс (или -1, если не сегодня). */
    public static int ticksUntil(ZoneData d) {
        if (scheduled > 0) return scheduled;
        if ((d.sim.day + 1) % 25 != 0) return -1;
        return StalkerZoneMod.TICKS_PER_SIM_DAY - d.tickCounter;
    }

    /** Примета перед выбросом: аномалии затихают. */
    public static boolean omen(MinecraftServer server) {
        int u = ticksUntil(ZoneData.get(server));
        return u >= 0 && u <= OMEN;
    }

    /** Окно сбора: до конца суток выброса, когда сам выброс прошёл. */
    public static boolean harvest(MinecraftServer server) {
        Simulation sim = ZoneData.get(server).sim;
        return sim.lastEmissionDay == sim.day && !active();
    }

    /** Множитель активности мутантов по суткам после выброса (канон ТЧ: затишье, пик на 3–4-е сутки). */
    public static double mutantActivity(Simulation sim) { return sim.mutantActivity(); }

    /** Старый вызов (за 30 с до конца суток) больше не нужен: этапы тревоги идут из {@link #tick}. */
    public static void warn(MinecraftServer server) {}

    public static void start(MinecraftServer server) {
        ticksLeft = DURATION;
        ServerLevel level = server.overworld();
        level.setWeatherParameters(0, DURATION + 600, true, true);
        for (ServerPlayer p : level.players()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Найдите укрытие под крышей").withStyle(ChatFormatting.GRAY)));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("ВЫБРОС").withStyle(ChatFormatting.DARK_RED)));
            p.playNotifySound(SoundEvents.WARDEN_ROAR, SoundSource.AMBIENT, 1.0f, 0.5f);
        }
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ZoneData d = ZoneData.get(server);
        if (scheduled > 0 && --scheduled == 0) {
            d.sim.triggerEmission();
            d.setDirty();
            start(server);
        }
        int until = ticksUntil(d);
        if (until > 0 && !active()) beforeEmission(level, d, until);
        if (gonLeft > 0) gon(level, d);
        else if (server.getTickCount() % 100 == 0) cleanupGon(level, 0);
        if (ticksLeft <= 0) return;
        ticksLeft--;
        during(level, d);
        if (ticksLeft == 0) end(level, d);
    }

    // ------------------------------------------------------------------ до выброса

    private static void beforeEmission(ServerLevel level, ZoneData d, int until) {
        if (until == OMEN) {
            pda(level, "Радио: «…на севере небо нездоровое, мужики. Аномалии притихли — не к добру. Держитесь поближе к укрытиям».", ChatFormatting.GOLD);
            for (ServerPlayer p : level.players()) p.playNotifySound(SoundEvents.WOLF_HOWL, SoundSource.AMBIENT, 0.6f, 0.7f);
        }
        if (until == ALARM) {
            level.setWeatherParameters(0, ALARM + DURATION + 600, true, false);
            for (ServerPlayer p : level.players()) {
                p.sendSystemMessage(Component.literal("[КПК] ВНИМАНИЕ! Приближается выброс! Всем немедленно в укрытие. " + shelterHint(d, p))
                        .withStyle(ChatFormatting.RED));
                p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.PLAYERS, 1.0f, 0.5f);
            }
        }
        if (until <= SIREN && until % 100 == 0) {
            for (ServerPlayer p : level.players()) {
                p.playNotifySound(SoundEvents.RAID_HORN.get(), SoundSource.AMBIENT, 0.7f, 0.8f);
                p.displayClientMessage(Component.literal("Выброс через " + until / 20 + " с! " + (level.canSeeSky(p.blockPosition()) ? "Вы под открытым небом!" : "Вы в укрытии."))
                        .withStyle(ChatFormatting.RED), true);
            }
        }
        if (until < OMEN && level.getServer().getTickCount() % 300 == 0)
            for (ServerPlayer p : level.players()) p.playNotifySound(SoundEvents.WOLF_HOWL, SoundSource.AMBIENT, 0.3f, 0.6f + RND.nextFloat() * 0.3f);
    }

    /** Ближайшее укрытие: подвал деревни, бункер, туннель, ангары АТП (карточка 013 — точки shelter уровня). */
    public static String shelterHint(ZoneData d, ServerPlayer p) {
        int[] best = nearestShelter(d, p.getX(), p.getZ());
        if (best != null) {
            double bd = Math.hypot(best[0] - p.getX(), best[2] - p.getZ());
            String name = d.sim.nodes.get(best[3]).name;
            if (bd < 40) return "Укрытие рядом: «" + name + "», ищите любую крышу или подвал.";
            String dir = direction(best[0] - p.getX(), best[2] - p.getZ());
            return "Ближайшее укрытие: «" + name + "», " + (int) bd + " м на " + dir + ". Подойдёт и любой подвал или дом.";
        }
        Node node = null;
        double bd = Double.MAX_VALUE;
        for (Node n : d.sim.nodes) {
            if (n.type == NodeType.ANOMALY || n.type == NodeType.PSI) continue;
            double dist = Math.hypot(n.x * ZoneBounds.SCALE - p.getX(), n.z * ZoneBounds.SCALE - p.getZ());
            if (dist < bd) { bd = dist; node = n; }
        }
        if (node == null) return "";
        if (bd < 40) return "Укрытие рядом: «" + node.name + "», ищите любую крышу или подвал.";
        String dir = direction(node.x * ZoneBounds.SCALE - p.getX(), node.z * ZoneBounds.SCALE - p.getZ());
        return "Ближайшее укрытие: «" + node.name + "», " + (int) bd + " м на " + dir + ". Подойдёт и любой подвал или дом.";
    }

    private static String direction(double dx, double dz) {
        return Math.abs(dx) > Math.abs(dz) * 2 ? (dx > 0 ? "восток" : "запад")
                : Math.abs(dz) > Math.abs(dx) * 2 ? (dz > 0 ? "юг" : "север")
                : (dz > 0 ? "юго-" : "северо-") + (dx > 0 ? "восток" : "запад");
    }

    /** Ближайшая точка-укрытие из файла точек уровня: world x,y,z и узел. null — если точек нет. */
    private static int[] nearestShelter(ZoneData d, double x, double z) {
        String levelId = ru.stalkerzone.world.LevelPoints.levelForScenario(d.sim.scenario);
        if (levelId == null) return null;
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (Node n : d.sim.nodes) {
            com.google.gson.JsonObject pts = ru.stalkerzone.world.LevelPoints.nodePoints(levelId, n.id);
            if (pts == null) continue;
            for (int[] s : ru.stalkerzone.world.LevelPoints.readVecs(pts, "shelter")) {
                if (s.length < 3) continue;
                double dist = Math.hypot(s[0] - x, s[2] - z);
                if (dist < bd) { bd = dist; best = new int[]{s[0], s[1], s[2], n.id}; }
            }
        }
        return best;
    }

    /** NPC бегут в ближайшее укрытие, а не стоят под открытым небом (карточка 013). */
    private static void shelterRun(ServerLevel level, ZoneData d) {
        // все загруженные сталкеры мира, независимо от игроков (иначе дальние так и останутся снаружи)
        for (net.minecraft.world.entity.Entity ent : level.getAllEntities()) {
            if (!(ent instanceof ru.stalkerzone.npc.StalkerEntity e) || !e.isAlive() || e.isTrader()) continue;
            if (!exposed(level, e)) continue; // уже под крышей — не трогаем
            int[] s = nearestShelter(d, e.getX(), e.getZ());
            if (s == null) continue;
            if (!level.hasChunk(s[0] >> 4, s[2] >> 4)) continue; // укрытие в незагруженном чанке
            e.getNavigation().moveTo(s[0] + 0.5, s[1], s[2] + 0.5, 1.3);
        }
    }

    private static void pda(ServerLevel level, String msg, ChatFormatting color) {
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.literal(msg).withStyle(color));
            p.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.6f, 1.2f);
        }
    }

    // ------------------------------------------------------------------ выброс

    private static void during(ServerLevel level, ZoneData d) {
        int elapsed = DURATION - ticksLeft;
        // сухие молнии вокруг (только вид и звук)
        if (ticksLeft % 30 == 0) for (ServerPlayer p : level.players()) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) continue;
            double a = RND.nextDouble() * Math.PI * 2, r = 24 + RND.nextDouble() * 40;
            int x = (int) (p.getX() + Math.cos(a) * r), z = (int) (p.getZ() + Math.sin(a) * r);
            bolt.moveTo(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z), z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        // карточка 013: NPC бегут в ближайшее укрытие, а не стоят под открытым небом
        if (ticksLeft % 100 == 0) shelterRun(level, d);
        if (ticksLeft % 40 != 0) return;
        // урон растёт к концу: от 2 до 7 раз в 2 секунды
        float dmg = 2.0f + 5.0f * elapsed / DURATION;
        for (ServerPlayer p : level.players()) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false));
            if (p.isCreative() || p.isSpectator()) continue;
            if (exposed(level, p)) {
                p.hurt(level.damageSources().magic(), dmg);
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, true, true));
                p.displayClientMessage(Component.literal("Под открытым небом нельзя: спрячьтесь под крышу!").withStyle(ChatFormatting.RED), true);
            } else if (ticksLeft % 200 == 0) {
                p.displayClientMessage(Component.literal("Стены дрожат, в ушах звенит… Ждите.").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /** Под открытым небом или под тонкой крышей (один блок от неба не спасает). */
    static boolean exposed(ServerLevel level, net.minecraft.world.entity.Entity e) {
        BlockPos head = e.blockPosition().above();
        if (level.canSeeSky(head)) return true;
        int solid = 0;
        for (int y = head.getY() + 1; y < level.getMaxBuildHeight() && solid < 2; y++) {
            BlockPos q = new BlockPos(head.getX(), y, head.getZ());
            if (level.getBlockState(q).isSolidRender(level, q)) solid++;
        }
        return solid < 1;
    }

    private static void end(ServerLevel level, ZoneData d) {
        // финальная волна: кто остался снаружи — погибает (костюм и здоровье могут спасти единицы)
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            if (exposed(level, p)) {
                p.hurt(level.damageSources().magic(), 26.0f);
                p.sendSystemMessage(Component.literal("Волна выброса прошла сквозь вас.").withStyle(ChatFormatting.DARK_RED));
            }
        }
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.literal("[КПК] Выброс закончился. Аномалии сместились, в них созрели свежие артефакты — до конца суток они мерцают, искать можно и без детектора. С севера идёт гон мутантов!")
                    .withStyle(ChatFormatting.YELLOW));
            p.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        gonLeft = GON;
    }

    // ------------------------------------------------------------------ гон

    private static void gon(ServerLevel level, ZoneData d) {
        gonLeft--;
        long t = level.getServer().getTickCount();
        // две-три стаи на игрока за время гона
        if (gonLeft % 500 == 250) for (ServerPlayer p : level.players()) spawnPack(level, p);
        if (t % 40 == 0) for (MutantEntity m : gonMutants(level)) {
            if (m.getTarget() == null) m.getNavigation().moveTo(m.getX() + RND.nextInt(9) - 4, m.getY(), m.getZ() + 40, 1.4);
        }
        if (gonLeft == 0) cleanupGon(level, 0);
    }

    private static void spawnPack(ServerLevel level, ServerPlayer p) {
        ZoneBounds b = ru.stalkerzone.world.ZoneWorld.bounds();
        int cx = (int) p.getX() + RND.nextInt(41) - 20, cz = (int) p.getZ() - 44 - RND.nextInt(12);
        if (!b.inside(cx, cz) || !level.hasChunk(cx >> 4, cz >> 4)) return;
        MutantType[] kinds = {MutantType.DOG, MutantType.DOG, MutantType.FLESH, MutantType.BOAR};
        MutantType kind = kinds[RND.nextInt(kinds.length)];
        int n = kind == MutantType.DOG ? 4 + RND.nextInt(3) : 2 + RND.nextInt(2);
        for (int i = 0; i < n; i++) {
            int x = cx + RND.nextInt(7) - 3, z = cz + RND.nextInt(7) - 3;
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos pos = SpawnFinder.groundAt(level, x, z);
            if (!level.getFluidState(pos).isEmpty()) continue;
            MutantEntity m = MutantRegistry.TYPES[kind.ordinal()].get().create(level);
            if (m == null) continue;
            m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180f, 0f);
            m.getPersistentData().putBoolean(GON_TAG, true);
            level.addFreshEntity(m);
        }
    }

    private static java.util.List<MutantEntity> gonMutants(ServerLevel level) {
        java.util.List<MutantEntity> out = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity e : level.getAllEntities())
            if (e instanceof MutantEntity m && m.isAlive() && m.getPersistentData().getBoolean(GON_TAG)) out.add(m);
        return out;
    }

    /** Убрать мутантов гона, ушедших далеко от игроков (или всех, если гон кончился давно). */
    private static void cleanupGon(ServerLevel level, int keepWithin) {
        for (MutantEntity m : gonMutants(level)) {
            double nearest = Double.MAX_VALUE;
            for (ServerPlayer p : level.players()) nearest = Math.min(nearest, p.distanceTo(m));
            if (nearest > 80 || (keepWithin > 0 && nearest > keepWithin)) m.discard();
        }
    }
}
