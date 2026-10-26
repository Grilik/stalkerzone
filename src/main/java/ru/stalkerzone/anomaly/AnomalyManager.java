package ru.stalkerzone.anomaly;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.item.ModItems;
import ru.stalkerzone.sim.Anomaly;
import ru.stalkerzone.sim.AnomalyType;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.ZoneBounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Аномалии в мире. Положение и тип берутся из симуляции, поэтому после выброса поля сдвигаются.
 * Каждая аномалия рисует свою анимацию частицами, действует на всех живых существ внутри радиуса
 * (игроков, сталкеров, мутантов) и время от времени рождает артефакт. Работает рядом с игроками.
 */
public final class AnomalyManager {
    /** Дальность, с которой аномалия живёт и видна. */
    private static final int ACTIVE = 72;

    private static final Map<Long, Integer> GROUND = new HashMap<>();
    private static final Random RND = new Random();

    /** v1.14: разряженные болтом аномалии (ключ — позиция) и до какого тика. */
    private static final Map<Long, Long> DISCHARGED = new HashMap<>();
    /** v1.14: выданные (болтом или «Сварогом») аномалии видны во всей красе до этого тика. */
    private static final Map<Long, Long> REVEALED = new HashMap<>();
    /** Дальше этого аномалия почти не видна: лишь редкое марево. */
    private static final int SUBTLE = 12;

    private AnomalyManager() {}

    static long key(int bx, int bz) { return ((long) bx << 32) ^ (bz & 0xffffffffL); }

    /** Аномалия под точкой (для болта). */
    public record Hit(long key, AnomalyType type, double x, double y, double z, double r) {}

    public static Hit anomalyAt(ServerLevel level, double px, double py, double pz) {
        Simulation sim = ZoneData.get(level.getServer()).sim;
        for (Anomaly a : sim.anomalies) {
            Node n = sim.nodes.get(a.node);
            int bx = n.x * ZoneBounds.SCALE + a.dx, bz = n.z * ZoneBounds.SCALE + a.dz;
            double r = a.type.radius * (0.8 + 0.2 * sim.anomalyPower(a));
            if (Math.abs(px - bx - 0.5) > r || Math.abs(pz - bz - 0.5) > r) continue;
            if (Math.hypot(px - bx - 0.5, pz - bz - 0.5) > r) continue;
            double y = GROUND.computeIfAbsent(key(bx, bz), k -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz)) + 0.1;
            if (py < y - 1 || py > y + 5) continue;
            return new Hit(key(bx, bz), a.type, bx + 0.5, y, bz + 0.5, r);
        }
        return null;
    }

    /**
     * Болт попал в аномалию: она срабатывает (вспышка, звук), выдаёт себя и на время разряжается.
     * Вернёт true, если болт уничтожен (мясорубка рвёт, жарка плавит).
     */
    public static boolean trigger(ServerLevel level, Hit h, net.minecraft.world.entity.Entity bolt) {
        long now = level.getServer().getTickCount();
        REVEALED.put(h.key(), now + 400);
        int calm = switch (h.type()) { case ELECTRA -> 60; case FOG -> 0; case GRINDER -> 140; default -> 100; };
        if (calm > 0) DISCHARGED.put(h.key(), now + calm);
        double x = bolt.getX(), y = bolt.getY(), z = bolt.getZ();
        switch (h.type()) {
            case SPRINGBOARD -> {
                level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
                level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 0.6f, 1.7f);
                bolt.setDeltaMovement(bolt.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 1.1, 0));
            }
            case WHIRLIGIG -> {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, h.x(), h.y() + 1.5, h.z(), 6, 1, 0.5, 1, 0);
                level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.AMBIENT, 0.8f, 0.6f);
                bolt.setDeltaMovement(-bolt.getDeltaMovement().z * 1.4, 0.6, bolt.getDeltaMovement().x * 1.4);
            }
            case GRINDER -> {
                level.sendParticles(ParticleTypes.CRIT, x, y, z, 25, 0.3, 0.3, 0.3, 0.3);
                level.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.AMBIENT, 0.6f, 0.5f);
                return true;
            }
            case MOSQUITO -> {
                level.sendParticles(ParticleTypes.CLOUD, x, h.y() + 0.2, z, 12, 0.8, 0.05, 0.8, 0.02);
                level.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.AMBIENT, 0.4f, 1.4f);
                bolt.setDeltaMovement(0, -2.0, 0);
            }
            case FRY -> {
                level.sendParticles(ParticleTypes.FLAME, h.x(), h.y() + 0.5, h.z(), 50, 0.4, 1.6, 0.4, 0.05);
                level.playSound(null, x, y, z, SoundEvents.BLAZE_SHOOT, SoundSource.AMBIENT, 0.8f, 0.6f);
                return true;
            }
            case FLUFF -> {
                level.sendParticles(ParticleTypes.SMALL_FLAME, x, y, z, 20, 0.6, 0.6, 0.6, 0.01);
                level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.AMBIENT, 0.5f, 1.4f);
            }
            case ELECTRA -> {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, h.x(), h.y() + 1, h.z(), 60, h.r() * 0.4, 1, h.r() * 0.4, 0.4);
                level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.AMBIENT, 0.8f, 1.5f);
                bolt.setDeltaMovement(bolt.getDeltaMovement().scale(-0.3));
            }
            case FOG -> {
                level.sendParticles(ParticleTypes.BUBBLE_POP, x, y, z, 20, 0.5, 0.5, 0.5, 0.02);
                level.playSound(null, x, y, z, SoundEvents.BREWING_STAND_BREW, SoundSource.AMBIENT, 0.6f, 0.8f);
            }
        }
        bolt.hurtMarked = true;
        return false;
    }

    /** «Сварог» и подобное: выдать аномалии в радиусе. */
    public static void revealNear(double px, double pz, double radius, long until) {
        lastReveal = new double[] {px, pz, radius, until};
    }

    private static double[] lastReveal;

    public static boolean discharged(long key, long now) {
        Long u = DISCHARGED.get(key);
        return u != null && u > now;
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        long t = server.getTickCount();
        if (t % 600 == 0) {
            GROUND.clear();
            DISCHARGED.values().removeIf(u -> u < t);
            REVEALED.values().removeIf(u -> u < t);
        }
        if (lastReveal != null) {
            double[] lr = lastReveal;
            lastReveal = null;
            for (Anomaly a : ZoneData.get(server).sim.anomalies) {
                Node n = ZoneData.get(server).sim.nodes.get(a.node);
                int bx = n.x * ZoneBounds.SCALE + a.dx, bz = n.z * ZoneBounds.SCALE + a.dz;
                if (Math.hypot(bx - lr[0], bz - lr[1]) <= lr[2]) REVEALED.merge(key(bx, bz), (long) lr[3], Math::max);
            }
        }
        ZoneData data = ZoneData.get(server);
        Simulation sim = ZoneData.get(server).sim;
        int index = 0;
        for (Anomaly a : sim.anomalies) {
            index++;
            Node n = sim.nodes.get(a.node);
            int bx = n.x * ZoneBounds.SCALE + a.dx, bz = n.z * ZoneBounds.SCALE + a.dz;
            List<ServerPlayer> near = new ArrayList<>();
            double closest = Double.MAX_VALUE;
            for (ServerPlayer p : players) {
                double dd = Math.hypot(p.getX() - bx, p.getZ() - bz);
                if (dd <= ACTIVE) { near.add(p); closest = Math.min(closest, dd); }
            }
            if (near.isEmpty() || !level.hasChunk(bx >> 4, bz >> 4)) continue;
            long k0 = key(bx, bz);
            Long rv = REVEALED.get(k0);
            boolean revealed = rv != null && rv > t, calm = discharged(k0, t);
            double y = GROUND.computeIfAbsent(((long) bx << 32) ^ (bz & 0xffffffffL),
                    k -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz)) + 0.1;
            double x = bx + 0.5, z = bz + 0.5;

            double power = sim.anomalyPower(a);
            double r = a.type.radius * (0.8 + 0.2 * power);
            // сильные поля видны гуще: частиц больше
            // v1.14: издали аномалию почти не видно (редкое марево), вблизи и после болта — во всей красе;
            // разряженная болтом еле теплится и не бьёт
            int every = calm ? 10 : (revealed || closest <= SUBTLE) ? 2 : 8;
            if (t % every == 0) for (int k = 0; k < (power >= 2.8 && every == 2 ? 2 : 1); k++) particles(level, near, a.type, r, x, y, z, t + k * 7);
            if (!calm && t % 10 == (index % 10)) effects(level, a.type, r, (float) (a.type.damage * power), x, y, z, t);
            if (t % 100 == (index * 7L) % 100) dropArtifacts(level, data, a, x, y, z);
        }
    }

    private static void emit(ServerLevel level, List<ServerPlayer> to, ParticleOptions p, double x, double y, double z,
                             int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer pl : to) level.sendParticles(pl, p, true, x, y, z, count, dx, dy, dz, speed);
    }

    /** Анимации: у каждого типа свой узнаваемый рисунок. */
    private static void particles(ServerLevel level, List<ServerPlayer> to, AnomalyType type, double r, double x, double y, double z, long t) {
        double a = t * 0.25;
        switch (type) {
            case SPRINGBOARD -> {
                for (int i = 0; i < 6; i++) {
                    double ang = i * Math.PI / 3 + a;
                    emit(level, to, ParticleTypes.END_ROD, x + Math.cos(ang) * r * 0.8, y + 0.2, z + Math.sin(ang) * r * 0.8, 1, 0, 0.3, 0, 0.05);
                }
                emit(level, to, ParticleTypes.CLOUD, x, y + 0.3 + (t % 30) * 0.05, z, 2, 0.3, 0.1, 0.3, 0.02);
            }
            case WHIRLIGIG -> {
                for (int i = 0; i < 5; i++) {
                    double ang = a * 1.5 + i * Math.PI * 2 / 5, rad = r * (0.3 + 0.6 * ((t % 40) / 40.0));
                    emit(level, to, ParticleTypes.PORTAL, x + Math.cos(ang) * rad, y + 0.4 + (t % 40) * 0.04, z + Math.sin(ang) * rad, 2, 0.05, 0.05, 0.05, 0.1);
                }
                emit(level, to, ParticleTypes.END_ROD, x, y + 1.5, z, 1, 0.4, 0.8, 0.4, 0.01);
            }
            case GRINDER -> {
                emit(level, to, ParticleTypes.SQUID_INK, x, y + 0.3, z, 4, r * 0.4, 0.1, r * 0.4, 0.02);
                emit(level, to, ParticleTypes.CRIT, x, y + 0.5, z, 3, r * 0.5, 0.3, r * 0.5, 0.2);
            }
            case MOSQUITO -> {
                for (int i = 0; i < 4; i++) {
                    double ang = -a * 2 + i * Math.PI / 2, rad = r * 0.5;
                    emit(level, to, ParticleTypes.WITCH, x + Math.cos(ang) * rad, y + 0.3, z + Math.sin(ang) * rad, 1, 0.1, 0.1, 0.1, 0.01);
                }
                emit(level, to, ParticleTypes.SQUID_INK, x, y + 0.2, z, 2, r * 0.4, 0.05, r * 0.4, 0.01);
            }
            case FRY -> {
                emit(level, to, ParticleTypes.FLAME, x, y + 0.2, z, 6, r * 0.35, 0.1, r * 0.35, 0.04);
                emit(level, to, ParticleTypes.LAVA, x, y + 0.5, z, 1, r * 0.3, 0.1, r * 0.3, 0);
                emit(level, to, ParticleTypes.LARGE_SMOKE, x, y + 1.0, z, 2, 0.3, 0.5, 0.3, 0.03);
            }
            case FLUFF -> {
                emit(level, to, ParticleTypes.ASH, x, y + 0.6, z, 8, r * 0.5, 0.5, r * 0.5, 0.01);
                emit(level, to, ParticleTypes.SMALL_FLAME, x, y + 0.2, z, 4, r * 0.5, 0.05, r * 0.5, 0.01);
            }
            case ELECTRA -> {
                emit(level, to, ParticleTypes.ELECTRIC_SPARK, x, y + 1.0, z, 8, r * 0.5, 1.0, r * 0.5, 0.4);
                if (t % 8 == 0) {
                    double ang = RND.nextDouble() * Math.PI * 2;
                    for (int s = 1; s <= 6; s++) {
                        emit(level, to, ParticleTypes.ELECTRIC_SPARK, x + Math.cos(ang) * s * r / 6, y + 0.3 + RND.nextDouble(), z + Math.sin(ang) * s * r / 6, 2, 0.1, 0.1, 0.1, 0.05);
                    }
                }
            }
            case FOG -> {
                emit(level, to, ParticleTypes.COMPOSTER, x, y + 0.5, z, 6, r * 0.5, 0.4, r * 0.5, 0.01);
                emit(level, to, ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 1.2, z, 6, r * 0.5, 0.8, r * 0.5, 0.01);
            }
        }
    }

    /** Воздействие на всех живых внутри радиуса. */
    private static void effects(ServerLevel level, AnomalyType type, double r, float damage, double x, double y, double z, long t) {
        AABB box = new AABB(x - r, y - 2, z - r, x + r, y + 6, z + r);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box, e -> {
            if (e instanceof ServerPlayer p && (p.isCreative() || p.isSpectator())) return false;
            return e.isAlive() && Math.hypot(e.getX() - x, e.getZ() - z) <= r;
        });
        if (victims.isEmpty()) return;
        boolean second = t % 20 < 10;
        for (LivingEntity e : victims) {
            // костюм снижает урон от аномалий и изнашивается сам
            ItemStack suit = e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            double prot = ru.stalkerzone.gear.SuitItem.protection(suit, type);
            float dmg = (float) (damage * (1 - prot));
            if (prot > 0 && second) suit.hurtAndBreak(Math.max(1, Math.round(damage / 2)), e,
                    v -> v.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.CHEST));
            switch (type) {
                case SPRINGBOARD -> {
                    Vec3 d = e.getDeltaMovement();
                    e.setDeltaMovement(d.x * 0.3, 1.5, d.z * 0.3);
                    e.hurtMarked = true;
                    e.hurt(level.damageSources().magic(), dmg * 0.3f);
                    level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 0.4f, 1.8f);
                }
                case WHIRLIGIG -> {
                    e.setYRot(e.getYRot() + 45);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.25, 0));
                    e.hurtMarked = true;
                    if (second) e.hurt(level.damageSources().magic(), dmg);
                }
                case GRINDER -> {
                    double dist = Math.hypot(e.getX() - x, e.getZ() - z);
                    Vec3 pull = new Vec3(x - e.getX(), 0, z - e.getZ()).scale(0.18);
                    e.setDeltaMovement(e.getDeltaMovement().add(pull));
                    e.hurtMarked = true;
                    if (dist < r * 0.5 && second) e.hurt(level.damageSources().magic(), dmg);
                }
                case MOSQUITO -> {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, true, true));
                    e.setDeltaMovement(e.getDeltaMovement().add(0, -0.15, 0));
                    if (second) e.hurt(level.damageSources().magic(), dmg);
                }
                case FRY -> {
                    e.setSecondsOnFire(4);
                    if (second) e.hurt(level.damageSources().magic(), dmg);
                }
                case FLUFF -> {
                    e.setSecondsOnFire(2);
                    e.hurt(level.damageSources().magic(), dmg);
                }
                case ELECTRA -> {
                    if (t % 20 < 10) {
                        e.hurt(level.damageSources().magic(), dmg);
                        e.knockback(0.8, x - e.getX(), z - e.getZ());
                        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.AMBIENT, 0.5f, 1.6f);
                    }
                }
                case FOG -> {
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, true, true));
                    if (second) e.hurt(level.damageSources().magic(), dmg);
                }
            }
        }
    }

    /**
     * v1.14: созревшие артефакты прячутся внутри поля — без детектора их не найти (см. {@link Artifacts}).
     * У одного поля копится не больше {@link Artifacts#CAP_PER_FIELD}, остальные ждут в симуляции.
     */
    private static void dropArtifacts(ServerLevel level, ZoneData data, Anomaly a, double x, double y, double z) {
        if (a.pending <= 0) return;
        int room = Artifacts.CAP_PER_FIELD - Artifacts.hiddenNear(data, x, z, a.type.radius + 4);
        if (room <= 0) return;
        int took = data.sim.takePendingArtifacts(a, room);
        for (int i = 0; i < took; i++) {
            double ox = (RND.nextDouble() - 0.5) * a.type.radius * 0.8, oz = (RND.nextDouble() - 0.5) * a.type.radius * 0.8;
            Artifacts.hide(data, a.type.ordinal(), x + ox, y, z + oz);
        }
    }
}
