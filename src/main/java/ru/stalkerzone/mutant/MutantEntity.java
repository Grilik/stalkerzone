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
                .add(Attributes.KNOCKBACK_RESISTANCE, t == MutantType.CHIMERA || t == MutantType.BOAR ? 0.6 : 0.1);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        if (kind() == MutantType.DOG || kind() == MutantType.SNORK || kind() == MutantType.CHIMERA) {
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
        if (kind() == MutantType.CONTROLLER && target != null && --psiCooldown <= 0) {
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

    @Override
    public void die(DamageSource source) {
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
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return switch (kind()) {
            case DOG -> SoundEvents.WOLF_HURT;
            case FLESH -> SoundEvents.PIG_HURT;
            case BOAR -> SoundEvents.HOGLIN_HURT;
            case CHIMERA -> SoundEvents.RAVAGER_HURT;
            default -> SoundEvents.ZOMBIE_HURT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return switch (kind()) {
            case DOG -> SoundEvents.WOLF_DEATH;
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
