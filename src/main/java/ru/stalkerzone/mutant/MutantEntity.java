package ru.stalkerzone.mutant;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.npc.StalkerEntity;
import ru.stalkerzone.sim.Lair;
import ru.stalkerzone.sim.Simulation;

/**
 * Мутант у логова. Привязан к логову по номеру точки: его гибель ослабляет логово в симуляции,
 * выбитое до конца логово уничтожается. Кровосос невидим, пока не подберётся вплотную;
 * контролёр бьёт пси-ударом на расстоянии.
 */
public class MutantEntity extends Monster {
    private MutantType type;
    private int lairNode = -1;
    private int psiCooldown = 60;

    public MutantEntity(EntityType<? extends MutantEntity> entityType, Level level, MutantType type) {
        super(entityType, level);
        this.type = type;
        this.xpReward = (int) Math.max(3, type.value / 2);
    }

    /** Вид мутанта. registerGoals вызывается из конструктора родителя, поэтому вид берём из типа сущности. */
    public MutantType kind() {
        if (type == null) type = MutantRegistry.typeOf(getType());
        return type;
    }

    public static AttributeSupplier.Builder attributes(MutantType t) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, t.health)
                .add(Attributes.MOVEMENT_SPEED, t.speed)
                .add(Attributes.ATTACK_DAMAGE, t.damage)
                .add(Attributes.ARMOR, t.armor)
                .add(Attributes.FOLLOW_RANGE, t == MutantType.CONTROLLER ? 32 : 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, t == MutantType.PSEUDOGIANT ? 1.0 : t == MutantType.CHIMERA || t == MutantType.BOAR ? 0.6 : 0.1);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        MutantType k0 = kind();
        if (k0 == MutantType.DOG || k0 == MutantType.SNORK || k0 == MutantType.CHIMERA || k0 == MutantType.PSEUDODOG || k0 == MutantType.TUSHKAN) {
            this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, kind() == MutantType.CHIMERA ? 0.55f : 0.4f));
        }
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, kind() == MutantType.CONTROLLER ? 0.8 : 1.2, true));
        this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, MutantEntity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, StalkerEntity.class, 10, true, false, e -> !((StalkerEntity) e).isTrader()));
    }

    public void bind(int lairNode, BlockPos den) {
        this.lairNode = lairNode;
        this.restrictTo(den, 22);
        this.setPersistenceRequired();
    }

    public int lairNode() { return lairNode; }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false; // за численностью следит MutantManager
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        LivingEntity target = getTarget();
        if (kind() == MutantType.BLOODSUCKER) {
            boolean close = target != null && distanceTo(target) < 5;
            if (!close) addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
            else removeEffect(MobEffects.INVISIBILITY);
        }
        MutantType k = kind();
        if (phantom() && level() instanceof net.minecraft.server.level.ServerLevel sl0 && sl0.getGameTime() > getPersistentData().getLong(PHANTOM)) {
            sl0.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, getX(), getY() + 0.5, getZ(), 10, 0.3, 0.3, 0.3, 0.02);
            discard();
            return;
        }
        if (k == MutantType.POLTERGEIST) poltergeistTick(target);
        if (k == MutantType.PSYDOG && target != null && --psiCooldown <= 0 && distanceTo(target) < 16) {
            psiCooldown = 200 + random.nextInt(100);
            psyDogIllusions(target);
        }
        if (k == MutantType.BURER && target != null && --psiCooldown <= 0 && distanceTo(target) < 14 && hasLineOfSight(target)) {
            psiCooldown = 90 + random.nextInt(50);
            burerTelekinesis(target);
        }
        if (k == MutantType.PSEUDOGIANT && target != null && --psiCooldown <= 0 && distanceTo(target) < 5) {
            psiCooldown = 70 + random.nextInt(30);
            giantStomp();
        }
        if (k == MutantType.CONTROLLER && target != null && --psiCooldown <= 0) {
            psiCooldown = 80 + random.nextInt(40);
            if (distanceTo(target) < 28 && hasLineOfSight(target)) {
                target.hurt(damageSources().indirectMagic(this, this), 5.0f);
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0));
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                playSound(SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.6f);
                if (target instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal("Голова раскалывается: рядом контролёр!").withStyle(ChatFormatting.DARK_PURPLE), true);
                }
            }
        }
    }

    // ------------------------------------------------------------------ v1.14: новые умения

    static final String PHANTOM = "sz_phantom";

    public boolean phantom() { return getPersistentData().contains(PHANTOM); }

    /** Пси-собака морочит голову: вокруг цели появляются двойники-призраки, пропадающие от первого удара. */
    private void psyDogIllusions(LivingEntity target) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        int n = 2 + random.nextInt(2);
        for (int i = 0; i < n; i++) {
            MutantEntity d = MutantRegistry.TYPES[MutantType.DOG.ordinal()].get().create(sl);
            if (d == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            d.moveTo(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, random.nextFloat() * 360, 0);
            d.getPersistentData().putLong(PHANTOM, sl.getGameTime() + 300);
            d.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1);
            d.setHealth(1);
            d.setTarget(target);
            sl.addFreshEntity(d);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, d.getX(), d.getY() + 0.5, d.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        }
        playSound(SoundEvents.WOLF_HOWL, 1.5f, 0.6f);
        if (target instanceof ServerPlayer sp) sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
    }

    /** Бюрер: телекинез — отшвыривает, давит и порой вырывает оружие из рук. */
    private void burerTelekinesis(LivingEntity target) {
        playSound(SoundEvents.EVOKER_CAST_SPELL, 1.5f, 0.5f);
        double dx = target.getX() - getX(), dz = target.getZ() - getZ(), d = Math.max(0.5, Math.hypot(dx, dz));
        target.push(dx / d * 1.6, 0.5, dz / d * 1.6);
        target.hurtMarked = true;
        target.hurt(damageSources().indirectMagic(this, this), 3.0f);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        if (target instanceof ServerPlayer sp && random.nextDouble() < 0.2 && !sp.getMainHandItem().isEmpty()) {
            ItemStack held = sp.getMainHandItem().copy();
            sp.getInventory().removeItem(sp.getInventory().selected, held.getCount());
            sp.drop(held, true);
            sp.displayClientMessage(Component.literal("Бюрер вырвал оружие из рук!").withStyle(ChatFormatting.RED), true);
        } else if (target instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal("Невидимая сила швыряет вас: рядом бюрер!").withStyle(ChatFormatting.GOLD), true);
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel sl)
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM, (getX() + target.getX()) / 2, target.getY() + 1, (getZ() + target.getZ()) / 2, 1, 0, 0, 0, 0);
    }

    /** Полтергейст: невидим, висит в воздухе, швыряет предметы (удар издали) и светится огоньком. */
    private void poltergeistTick(LivingEntity target) {
        setNoGravity(true);
        addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        if (tickCount % 4 == 0) sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.0, getZ(), 2, 0.1, 0.1, 0.1, 0.01);
        // держится на высоте 2-3 блока над землёй
        int ground = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, getBlockX(), getBlockZ());
        double want = ground + 2.5 - getY();
        setDeltaMovement(getDeltaMovement().x, Math.max(-0.1, Math.min(0.1, want * 0.1)), getDeltaMovement().z);
        if (target != null && --psiCooldown <= 0 && distanceTo(target) < 20 && hasLineOfSight(target)) {
            psiCooldown = 50 + random.nextInt(30);
            target.hurt(damageSources().indirectMagic(this, this), 4.0f);
            target.knockback(0.6, getX() - target.getX(), getZ() - target.getZ());
            sl.playSound(null, target.getX(), target.getY(), target.getZ(), random.nextBoolean() ? SoundEvents.ANVIL_LAND : SoundEvents.GLASS_BREAK,
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 0.8f + random.nextFloat() * 0.4f);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 12, 0.3, 0.5, 0.3, 0.2);
        }
    }

    /** Псевдогигант: удар о землю — волна, подбрасывает и бьёт всех вокруг. */
    private void giantStomp() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        playSound(SoundEvents.RAVAGER_ROAR, 2.0f, 0.5f);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 6, 3, 0.2, 3, 0);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6), x -> x != this && !(x instanceof MutantEntity))) {
            if (e instanceof ServerPlayer p && (p.isCreative() || p.isSpectator())) continue;
            double dist = e.distanceTo(this);
            if (dist > 6 || !e.onGround()) continue;
            e.hurt(damageSources().mobAttack(this), (float) (12 * (1 - dist / 8)));
            e.push(0, 0.7, 0);
            e.hurtMarked = true;
        }
    }

    @Override
    public void die(DamageSource source) {
        if (phantom()) {   // двойник пси-собаки просто тает
            if (level() instanceof net.minecraft.server.level.ServerLevel sl)
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, getX(), getY() + 0.5, getZ(), 15, 0.3, 0.3, 0.3, 0.02);
            discard();
            return;
        }
        super.die(source);
        if (level().isClientSide || lairNode < 0) return;
        // гибель в аномалии или от падения логово не ослабляет: считаем только убийства
        MutantManager.lairMobKilled(this, lairNode, kind(), source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lair", lairNode);
        BlockPos c = getRestrictCenter();
        tag.putIntArray("Den", new int[] {c.getX(), c.getY(), c.getZ(), (int) getRestrictRadius()});
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lairNode = tag.contains("Lair") ? tag.getInt("Lair") : -1;
        int[] d = tag.getIntArray("Den");
        if (d.length == 4 && d[3] > 0) restrictTo(new BlockPos(d[0], d[1], d[2]), d[3]);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return switch (kind()) {
            case DOG -> SoundEvents.WOLF_GROWL;
            case FLESH -> SoundEvents.PIG_AMBIENT;
            case BOAR -> SoundEvents.HOGLIN_AMBIENT;
            case SNORK -> SoundEvents.HUSK_AMBIENT;
            case BLOODSUCKER -> SoundEvents.ZOMBIE_AMBIENT;
            case CHIMERA -> SoundEvents.RAVAGER_AMBIENT;
            case CONTROLLER -> SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
            case PSEUDODOG, PSYDOG -> SoundEvents.WOLF_GROWL;
            case TUSHKAN -> SoundEvents.SILVERFISH_AMBIENT;
            case BURER -> SoundEvents.PIGLIN_BRUTE_AMBIENT;
            case POLTERGEIST -> SoundEvents.VEX_AMBIENT;
            case PSEUDOGIANT -> SoundEvents.RAVAGER_AMBIENT;
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return switch (kind()) {
            case DOG, PSEUDODOG, PSYDOG -> SoundEvents.WOLF_HURT;
            case TUSHKAN -> SoundEvents.SILVERFISH_HURT;
            case POLTERGEIST -> SoundEvents.VEX_HURT;
            case PSEUDOGIANT -> SoundEvents.RAVAGER_HURT;
            case FLESH -> SoundEvents.PIG_HURT;
            case BOAR -> SoundEvents.HOGLIN_HURT;
            case CHIMERA -> SoundEvents.RAVAGER_HURT;
            default -> SoundEvents.ZOMBIE_HURT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return switch (kind()) {
            case DOG, PSEUDODOG, PSYDOG -> SoundEvents.WOLF_DEATH;
            case TUSHKAN -> SoundEvents.SILVERFISH_DEATH;
            case POLTERGEIST -> SoundEvents.VEX_DEATH;
            case PSEUDOGIANT -> SoundEvents.RAVAGER_DEATH;
            case FLESH -> SoundEvents.PIG_DEATH;
            case BOAR -> SoundEvents.HOGLIN_DEATH;
            case CHIMERA -> SoundEvents.RAVAGER_DEATH;
            default -> SoundEvents.ZOMBIE_DEATH;
        };
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * (kind() == MutantType.BLOODSUCKER || kind() == MutantType.CHIMERA ? 0.6f : 0.85f);
    }
}
