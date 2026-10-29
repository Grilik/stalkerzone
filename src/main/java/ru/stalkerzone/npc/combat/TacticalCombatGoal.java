package ru.stalkerzone.npc.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import ru.stalkerzone.compat.CompatConfig;
import ru.stalkerzone.compat.GunAttackGoal;
import ru.stalkerzone.compat.Tacz;
import ru.stalkerzone.npc.StalkerEntity;
import ru.stalkerzone.sim.tactics.Accuracy;
import ru.stalkerzone.sim.tactics.CoverFinder;
import ru.stalkerzone.sim.tactics.Morale;
import ru.stalkerzone.sim.tactics.SquadOrders;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Бой с огнестрелом (v1.16) вместо «встал и стреляет»:
 * <ul>
 *   <li>боец ищет укрытие на дистанции боя, перебегает к нему, приседает за низким, выглядывает и бьёт очередью;</li>
 *   <li>перезаряжается в укрытии, при обходе противника меняет укрытие;</li>
 *   <li>отряд получает приказы: командир, прижимающие огнём, фланговые (обход слева/справа), медики позади;</li>
 *   <li>меткость по навыку, морали, движению, укрытию и подавлению (Accuracy);</li>
 *   <li>в панике отходит в дальнее укрытие и палит не глядя, сломленный уходит из боя (FleeGoal).</li>
 * </ul>
 * Без огнестрела TaCZ цель не активна: работает рукопашная.
 */
public class TacticalCombatGoal extends Goal {
    enum Phase { ADVANCE, TO_COVER, IN_COVER, PEEK, OPEN }

    private final StalkerEntity mob;
    private final GunAttackGoal.Refill refill;
    private Phase phase = Phase.ADVANCE;
    private CoverFinder.Spot cover;
    private int phaseTicks, coverAge, seeTime, burstLeft, pause, reloadTicks, drawn = -1000, reaction, lastSeenTick = -1000, strafeTicks, nextCoverTry;
    private boolean strafeLeft, announced;
    private Vec3 lastSeen;

    public TacticalCombatGoal(StalkerEntity mob, GunAttackGoal.Refill refill) {
        this.mob = mob;
        this.refill = refill;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private CompatConfig.GunSpec spec() {
        ItemStack s = mob.getMainHandItem();
        if (!Tacz.isGun(s)) return null;
        CompatConfig.GunSpec g = CompatConfig.GUNS.get(Tacz.gunId(s));
        return g != null ? g : new CompatConfig.GunSpec(Tacz.gunId(s), "", 30, "SEMI", 2, 24, 2, List.of());
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && !mob.isDowned() && spec() != null && mob.combat().morale.state() != Morale.State.BROKEN;
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        seeTime = 0; burstLeft = 0; reloadTicks = 0; cover = null; phase = Phase.ADVANCE; announced = false;
        pause = 6 + mob.getRandom().nextInt(10);
        reaction = Accuracy.reactionTicks(mob.combatSkill(), mob.combat().morale.state());
        Tacz.draw(mob);
        drawn = mob.tickCount;
        mob.setAggressive(true);
        updateSquad();
    }

    @Override
    public void stop() {
        Tacz.aim(mob, false);
        mob.setAggressive(false);
        mob.setCrouch(false);
        mob.combat().inCover = false;
        mob.getNavigation().stop();
        cover = null;
    }

    // ------------------------------------------------------------------ отряд

    private void updateSquad() {
        CombatState cs = mob.combat();
        List<StalkerEntity> allies = mob.level().getEntitiesOfClass(StalkerEntity.class, mob.getBoundingBox().inflate(24),
                e -> e.isAlive() && !e.isDowned() && !e.isTrader() && e.faction() == mob.faction() && e.getTarget() != null);
        if (!allies.contains(mob)) allies.add(mob);
        List<SquadOrders.Member> ms = new ArrayList<>();
        StalkerEntity leader = null;
        for (StalkerEntity a : allies) {
            ms.add(new SquadOrders.Member(a.getUUID().getMostSignificantBits(), a.commandScore(), a.bravery(), a.isMedic(),
                    a.combat().morale.state() == Morale.State.STEADY, Tacz.isGun(a.getMainHandItem())));
        }
        List<SquadOrders.Assignment> as = SquadOrders.assign(ms);
        SquadOrders.Order prev = cs.order;
        cs.order = SquadOrders.orderOf(as, mob.getUUID().getMostSignificantBits());
        for (StalkerEntity a : allies) if (SquadOrders.orderOf(as, a.getUUID().getMostSignificantBits()) == SquadOrders.Order.LEADER) leader = a;
        cs.leaderNear = (leader != null && leader != mob && leader.distanceToSqr(mob) < 16 * 16) || mob.namedLeaderNear();
        LivingEntity t = mob.getTarget();
        int enemies = 1;
        if (t instanceof StalkerEntity ts) {
            enemies = mob.level().getEntitiesOfClass(StalkerEntity.class, t.getBoundingBox().inflate(16),
                    e -> e.isAlive() && !e.isDowned() && e.faction() == ts.faction()).size();
        } else if (t instanceof Player) {
            enemies = mob.level().getEntitiesOfClass(Player.class, t.getBoundingBox().inflate(16), p -> p.isAlive() && !p.isSpectator()).size();
        } else if (t != null) {
            enemies = mob.level().getEntitiesOfClass(t.getClass(), t.getBoundingBox().inflate(16), LivingEntity::isAlive).size();
        }
        cs.enemyRatio = enemies / (double) Math.max(1, allies.size());
        if (allies.size() >= 2 && prev != cs.order) {
            switch (cs.order) {
                case LEADER -> { if (!announced) cs.shout(mob, pick("Контакт! Занять укрытия!", "К бою! Рассредоточиться!", "Противник! Все в укрытия!"), true); }
                case FLANK_LEFT -> cs.shout(mob, "Обхожу слева!", false);
                case FLANK_RIGHT -> cs.shout(mob, "Захожу справа!", false);
                case MEDIC -> { }
                default -> { if (mob.getRandom().nextInt(3) == 0) cs.shout(mob, pick("Прикрываю!", "Держу их!", "Прижимаю огнём!"), false); }
            }
            announced = true;
            cover = null;   // новый приказ — новое место
        }
    }

    private String pick(String... s) { return s[mob.getRandom().nextInt(s.length)]; }

    // ------------------------------------------------------------------ укрытие

    private CoverFinder.Wish wish(CompatConfig.GunSpec g, boolean panic) {
        double ideal = Math.max(8, g.range() * 0.65);
        CombatState cs = mob.combat();
        if (panic) return new CoverFinder.Wish(g.range() * 1.2, 0, true, 14);
        return switch (cs.order) {
            case FLANK_LEFT -> new CoverFinder.Wish(ideal * 0.8, 1, false, 12);
            case FLANK_RIGHT -> new CoverFinder.Wish(ideal * 0.8, -1, false, 12);
            case MEDIC -> new CoverFinder.Wish(ideal * 1.3, 0, false, 10);
            case LEADER -> new CoverFinder.Wish(ideal * 1.05, 0, false, 10);
            default -> new CoverFinder.Wish(ideal, 0, false, 10);
        };
    }

    private void seekCover(LivingEntity t, CompatConfig.GunSpec g, boolean panic) {
        if (mob.tickCount < nextCoverTry) return;
        nextCoverTry = mob.tickCount + 30 + mob.getRandom().nextInt(20);
        Vec3 eye = t.getEyePosition();
        CoverFinder.Spot s = CoverFinder.find(new WorldGrid(mob.level()), mob.getBlockX(), mob.getBlockY(), mob.getBlockZ(),
                eye.x, eye.y, eye.z, wish(g, panic), 70, new java.util.Random(mob.getRandom().nextLong()));
        if (s != null) {
            cover = s;
            coverAge = 0;
            phase = Phase.TO_COVER;
            phaseTicks = 140;
            mob.getNavigation().moveTo(s.x() + 0.5, s.y(), s.z() + 0.5, panic ? 1.3 : 1.15);
        } else if (phase != Phase.ADVANCE) {
            phase = Phase.OPEN;
        }
    }

    private boolean atCover() {
        return cover != null && mob.distanceToSqr(cover.x() + 0.5, cover.y(), cover.z() + 0.5) < 1.3 * 1.3;
    }

    /** Укрытие ещё прикрывает от противника? */
    private boolean coverHolds(LivingEntity t) {
        if (cover == null) return false;
        Vec3 e = t.getEyePosition();
        WorldGrid g = new WorldGrid(mob.level());
        return CoverFinder.blocked(g, e.x, e.y, e.z, cover.x() + 0.5, cover.y() + (cover.low() ? 0.9 : 1.62), cover.z() + 0.5);
    }

    // ------------------------------------------------------------------ тик

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        CompatConfig.GunSpec g = spec();
        if (t == null || g == null) return;
        CombatState cs = mob.combat();
        double d = mob.distanceTo(t);
        boolean see = mob.getSensing().hasLineOfSight(t);
        seeTime = see ? Math.min(seeTime + 1, 100) : Math.max(seeTime - 1, -60);
        if (see) { lastSeen = t.position(); lastSeenTick = mob.tickCount; }
        if (cs.suppressedTicks > 0) cs.suppressedTicks--;
        if (mob.tickCount % 40 == 0) updateSquad();
        if (mob.tickCount % 20 == 0) cs.morale.second(cs.enemyRatio, cs.inCover, cs.leaderNear, cs.suppressed() || mob.hurtTime > 0);
        Morale.State ms = cs.morale.state();
        boolean panic = ms == Morale.State.PANIC;
        if (see) mob.getLookControl().setLookAt(t, 30f, 30f);
        else if (lastSeen != null) mob.getLookControl().setLookAt(lastSeen.x, lastSeen.y + 1.4, lastSeen.z, 30f, 30f);
        coverAge++;

        // перезарядка: в укрытии, присев
        if (reloadTicks > 0) {
            if (cover != null && !atCover()) mob.getNavigation().moveTo(cover.x() + 0.5, cover.y(), cover.z() + 0.5, 1.2);
            else { mob.getNavigation().stop(); mob.setCrouch(true); }
            if (--reloadTicks == 0) {
                if (refill.refill(mob, g)) Tacz.setAmmoIn(mob.getMainHandItem(), g.mag());
                else {
                    mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);   // патронов нет — в рукопашную
                    cs.shout(mob, "Пустой! Патронов нет!", true);
                }
                Tacz.draw(mob);
                drawn = mob.tickCount;
            }
            return;
        }

        // паника: дальнее укрытие
        if (panic && mob.tickCount % 20 == 0 && (cover == null || coverAge > 200 || mob.distanceToSqr(t) < 64)) {
            if (cover == null || coverAge > 60) {
                cs.shout(mob, pick("Отходим!", "Их слишком много!", "Прикройте, отхожу!"), true);
                nextCoverTry = 0;
                seekCover(t, g, true);
            }
        }

        switch (phase) {
            case ADVANCE -> {
                mob.setCrouch(false);
                cs.inCover = false;
                if (see && d <= g.range() * 1.1) { nextCoverTry = 0; seekCover(t, g, panic); if (cover == null) phase = Phase.OPEN; }
                else {
                    Vec3 goal = lastSeen != null && mob.tickCount - lastSeenTick < 200 ? lastSeen : t.position();
                    mob.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.05);
                    // прижимающие огнём бьют по последнему месту, где видели
                    if (!see && cs.order == SquadOrders.Order.SUPPRESS && lastSeen != null && mob.tickCount - lastSeenTick < 80 && d < g.range())
                        fire(t, lastSeen.add(0, 1.2, 0), 1.6, false);
                }
            }
            case TO_COVER -> {
                cs.inCover = false;
                mob.setCrouch(false);
                if (atCover()) {
                    mob.getNavigation().stop();
                    phase = Phase.IN_COVER;
                    phaseTicks = panic ? 60 + mob.getRandom().nextInt(60) : 20 + mob.getRandom().nextInt(35);
                } else if (--phaseTicks <= 0 || mob.getNavigation().isDone()) {
                    if (!atCover()) { cover = null; phase = Phase.OPEN; }
                } else if (see && ms == Morale.State.STEADY && mob.getRandom().nextInt(3) == 0) {
                    fire(t, aimPoint(t), 1.0, true);   // огонь на бегу — редко и мимо
                }
            }
            case IN_COVER -> {
                cs.inCover = true;
                mob.getNavigation().stop();
                mob.setCrouch(true);
                if (Tacz.ammoIn(mob.getMainHandItem()) <= 0) { startReload(); return; }
                if (mob.tickCount % 20 == 0 && !coverHolds(t)) {   // обошли — меняем укрытие
                    if (mob.getRandom().nextBoolean()) cs.shout(mob, "Меня обходят!", false);
                    nextCoverTry = 0;
                    seekCover(t, g, panic);
                    return;
                }
                if (panic && see && mob.getRandom().nextInt(25) == 0) fire(t, aimPoint(t), 1.0, false); // не глядя
                if (--phaseTicks <= 0) {
                    phase = Phase.PEEK;
                    phaseTicks = (cs.order == SquadOrders.Order.SUPPRESS ? 35 : 22) + mob.getRandom().nextInt(25);
                    if (panic) phaseTicks /= 2;
                }
            }
            case PEEK -> {
                cs.inCover = false;
                if (cover == null) { phase = Phase.OPEN; return; }
                if (cover.low()) { mob.setCrouch(false); mob.getNavigation().stop(); }
                else {
                    mob.setCrouch(false);
                    if (mob.distanceToSqr(cover.peekX() + 0.5, cover.peekY(), cover.peekZ() + 0.5) > 0.6)
                        mob.getNavigation().moveTo(cover.peekX() + 0.5, cover.peekY(), cover.peekZ() + 0.5, 1.0);
                    else mob.getNavigation().stop();
                }
                if (see) fire(t, aimPoint(t), 1.0, false);
                else if (cs.order == SquadOrders.Order.SUPPRESS && lastSeen != null && mob.tickCount - lastSeenTick < 80)
                    fire(t, lastSeen.add(0, 1.2, 0), 1.6, false);
                if (--phaseTicks <= 0) {
                    phase = Phase.TO_COVER;
                    phaseTicks = 60;
                    mob.getNavigation().moveTo(cover.x() + 0.5, cover.y(), cover.z() + 0.5, 1.1);
                    // фланговый, дошедший до места, или если враг ушёл далеко — ищем новое укрытие
                    if (coverAge > 400 || d > g.range() * 1.2) { nextCoverTry = 0; seekCover(t, g, panic); }
                }
            }
            case OPEN -> {
                cs.inCover = false;
                // укрытия не нашлось: держать дистанцию, стрейфиться, опытные стреляют с колена
                double ideal = Math.max(6, g.range() * 0.6);
                boolean moving;
                if (!see || d > g.range()) { mob.getNavigation().moveTo(t, 1.05); moving = true; }
                else if (d < Math.min(5, ideal * 0.5)) {
                    Vec3 away = mob.position().subtract(t.position()).normalize().scale(6);
                    mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.15);
                    moving = true;
                } else {
                    mob.getNavigation().stop();
                    if (--strafeTicks <= 0) { strafeTicks = 20 + mob.getRandom().nextInt(40); strafeLeft = mob.getRandom().nextBoolean(); }
                    boolean kneel = mob.combatSkill() >= 50 && seeTime > 10 && mob.getRandom().nextInt(100) < 70;
                    mob.setCrouch(kneel);
                    if (!kneel) mob.getMoveControl().strafe(d > ideal * 1.3 ? 0.35f : 0f, strafeLeft ? 0.35f : -0.35f);
                    moving = !kneel;
                }
                if (see) fire(t, aimPoint(t), 1.0, moving);
                if (mob.tickCount % 60 == 0) seekCover(t, g, panic);
            }
        }
    }

    private Vec3 aimPoint(LivingEntity t) {
        return t.position().add(0, t.getBbHeight() * (t.isCrouching() ? 0.55 : 0.62), 0);
    }

    private void startReload() {
        reloadTicks = 50;
        Tacz.aim(mob, false);
        if (mob.getRandom().nextInt(3) == 0) mob.combat().shout(mob, pick("Перезаряжаюсь!", "Меняю магазин!", "Прикройте!"), false);
    }

    /** Выстрел с разбросом по Accuracy. extra — дополнительный множитель (огонь по памяти, вслепую). */
    private void fire(LivingEntity t, Vec3 aimAt, double extra, boolean moving) {
        CompatConfig.GunSpec g = spec();
        if (g == null) return;
        if (seeTime < reaction && extra <= 1.0) { Tacz.aim(mob, false); return; }
        if (mob.tickCount - drawn < 12) return;
        Tacz.aim(mob, true);
        if (pause > 0) { pause--; return; }
        CombatState cs = mob.combat();
        Accuracy.Shot shot = new Accuracy.Shot(mob.combatSkill(), mob.distanceTo(t), cs.morale.state(),
                t.getDeltaMovement().horizontalDistanceSqr() > 0.003, t.isCrouching(),
                moving || mob.getDeltaMovement().horizontalDistanceSqr() > 0.004, cs.suppressed(),
                mob.isCrouching() && !moving, mob.getHealth() < mob.getMaxHealth() * 0.5, cs.leaderNear);
        double spread = Accuracy.spreadDeg(shot) * extra;
        if (Double.isInfinite(spread)) return;
        Vec3 eye = mob.getEyePosition();
        Vec3 dv = aimAt.subtract(eye);
        double h = Math.sqrt(dv.x * dv.x + dv.z * dv.z);
        float yaw = (float) (Mth.atan2(dv.z, dv.x) * Mth.RAD_TO_DEG) - 90f + (float) ((mob.getRandom().nextDouble() * 2 - 1) * spread);
        float pitch = (float) (-(Mth.atan2(dv.y, h) * Mth.RAD_TO_DEG)) + (float) ((mob.getRandom().nextDouble() * 2 - 1) * spread / 2);
        String r = Tacz.shoot(mob, pitch, yaw);
        switch (r) {
            case "SUCCESS" -> {
                if (t instanceof StalkerEntity ts) ts.combat().suppress();
                if (burstLeft <= 0) burstLeft = Math.max(1, g.burst() / 2 + mob.getRandom().nextInt(Math.max(1, g.burst())));
                if (--burstLeft <= 0) pause = "AUTO".equals(g.mode()) ? 12 + mob.getRandom().nextInt(25) : 8 + mob.getRandom().nextInt(20);
            }
            case "NO_AMMO" -> startReload();
            case "NOT_DRAW", "NOT_GUN" -> { Tacz.draw(mob); drawn = mob.tickCount; }
            default -> { }
        }
    }
}
