package ru.stalkerzone.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.sim.Faction;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Report;
import ru.stalkerzone.sim.Simulation;

/**
 * Сталкер или торговец. Игровое отражение симуляции: фракция и точка привязки хранятся в сущности,
 * гибель сталкера уменьшает численность фракции, торговец показывает цены своего рынка.
 */
public class StalkerEntity extends PathfinderMob {
    public static final int ROLE_STALKER = 0, ROLE_TRADER = 1, ROLE_BUILDER = 2;

    private static final EntityDataAccessor<Integer> FACTION = SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DOWNED = SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.BOOLEAN);

    /** Именной сталкер симуляции (0: рядовой). */
    private int personId;
    private boolean medic;
    private int downTicks;

    /** Номер локации, к которой привязан NPC. */
    private int homeNode = -1;
    private int homeX, homeY, homeZ;

    public StalkerEntity(EntityType<? extends StalkerEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(FACTION, 0);
        this.entityData.define(ROLE, ROLE_STALKER);
        this.entityData.define(DOWNED, false);
    }

    public int faction() { return this.entityData.get(FACTION); }
    public int role() { return this.entityData.get(ROLE); }
    public int homeNode() { return homeNode; }
    public boolean isTrader() { return role() == ROLE_TRADER; }
    public boolean isBuilder() { return role() == ROLE_BUILDER; }
    public boolean isDowned() { return this.entityData.get(DOWNED); }
    public boolean isMedic() { return medic; }
    public int personId() { return personId; }

    public void setPerson(int id) { this.personId = id; }
    public void setMedic(boolean m) { this.medic = m; }

    /** Настроить NPC при появлении в мире. */
    public void configure(int faction, int role, int node, BlockPos home, Component name) {
        this.entityData.set(FACTION, faction);
        this.entityData.set(ROLE, role);
        this.homeNode = node;
        this.homeX = home.getX();
        this.homeY = home.getY();
        this.homeZ = home.getZ();
        this.setCustomName(name);
        this.setCustomNameVisible(role == ROLE_TRADER);
        this.restrictTo(home, role == ROLE_TRADER ? 4 : role == ROLE_BUILDER ? 6 : 28);
        if (role == ROLE_TRADER) {
            this.setNoAi(true);
            this.setInvulnerable(true);
        } else if (role == ROLE_BUILDER) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            this.setDropChance(EquipmentSlot.MAINHAND, 0f);
        } else {
            equip(faction);
        }
    }

    /** Лидеры и заместители вооружены лучше. */
    public void equipLeader() {
        this.elite = true;
        if (!equipGun(true)) this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        equipSuit(true);
        this.setDropChance(EquipmentSlot.MAINHAND, 0f);
        this.setDropChance(EquipmentSlot.CHEST, 0f);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
        this.setHealth(40);
    }

    /** Профессиональная группа (снайперы, штурмовики, охрана лидеров): лучше одеты, стреляют заводским (v1.12). */
    private boolean elite;

    /** Костюм группировки: рядовым — кустарное своей мастерской, профессионалам — лучшие линейки или заводское. */
    private void equipSuit(boolean leader) {
        Simulation sim = sim();
        if (sim == null || faction() >= sim.factions.size() || this.level().isClientSide) return;
        ru.stalkerzone.sim.Faction f = sim.factions.get(faction());
        ru.stalkerzone.gear.Suit suit = ru.stalkerzone.gear.FactionGear.suit(f, leader || elite, new java.util.Random(this.random.nextLong()));
        ItemStack st = new ItemStack(ru.stalkerzone.item.ModItems.SUITS[suit.ordinal()].get());
        // носят не с иголочки: износ 10–50%
        st.setDamageValue((int) (st.getMaxDamage() * (0.1 + 0.4 * this.random.nextDouble())));
        this.setItemSlot(EquipmentSlot.CHEST, st);
        this.setDropChance(EquipmentSlot.CHEST, 0f); // снаряжение с тела берётся со склада фракции (добыча в dropLoot)
    }

    /** Огнестрел TaCZ по раскладке фракции (config/stalkerzone/compat.json). false — TaCZ нет или раскладка пуста. */
    private boolean equipGun(boolean leader) {
        if (!ru.stalkerzone.compat.Tacz.loaded() || this.level().isClientSide) return false;
        Simulation sim = sim();
        if (sim == null || faction() >= sim.factions.size()) return false;
        ru.stalkerzone.compat.CompatConfig.GunSpec g = ru.stalkerzone.compat.CompatConfig.pickGun(sim.factions.get(faction()).name, leader, new java.util.Random(this.random.nextLong()));
        if (g == null) return false;
        ItemStack gun = ru.stalkerzone.compat.Tacz.gun(g.id(), g.mode(), g.mag());
        if (gun.isEmpty()) return false;
        boolean factory = ru.stalkerzone.gear.FactionGear.factoryGun(sim.factions.get(faction()), leader || elite);
        ru.stalkerzone.compat.GunQuality.markGun(gun, !factory);
        this.setItemSlot(EquipmentSlot.MAINHAND, gun);
        this.setDropChance(EquipmentSlot.MAINHAND, 0f);
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(Math.max(24, g.range() + 8));
        return true;
    }

    /** Новый магазин со склада фракции: профессионалы и армия берут заводские патроны, остальные кустарные. */
    private static boolean refillFromFaction(net.minecraft.world.entity.PathfinderMob mob, ru.stalkerzone.compat.CompatConfig.GunSpec g) {
        if (!(mob instanceof StalkerEntity se)) return true;
        Simulation sim = se.sim();
        if (sim == null) return true;
        int units = Math.max(1, (int) Math.ceil(g.mag() / (double) ru.stalkerzone.compat.CompatConfig.roundsPerUnit(g.ammo())));
        ru.stalkerzone.sim.Good used = null;
        for (int i = 0; i < units; i++) {
            ru.stalkerzone.sim.Good u = sim.npcUseAmmo(se.faction(), se.elite);
            if (u == null) break;
            used = u;
        }
        if (used == null) return false;
        ru.stalkerzone.compat.GunQuality.markLoaded(se.getMainHandItem(), used == ru.stalkerzone.sim.Good.AMMO);
        return true;
    }

    private void equip(int faction) {
        Simulation sim0 = sim();
        if (sim0 != null && faction < sim0.factions.size())
            this.elite = ru.stalkerzone.gear.FactionGear.elite(sim0.factions.get(faction), false, new java.util.Random(this.random.nextLong()));
        equipSuit(false);
        if (faction != 4 && equipGun(false)) return;
        if (faction == 4 && this.random.nextInt(3) == 0 && equipGun(false)) return;
        ItemStack weapon = switch (faction) {
            case 1, 5 -> new ItemStack(Items.IRON_SWORD);   // Долг и Военные
            case 3 -> new ItemStack(Items.IRON_AXE);        // Бандиты
            case 6 -> new ItemStack(Items.NETHERITE_SWORD); // Монолит
            case 4 -> ItemStack.EMPTY;                      // Учёные без оружия
            default -> new ItemStack(Items.STONE_SWORD);
        };
        this.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        this.setDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ReviveAllyGoal(this));
        this.goalSelector.addGoal(2, new ru.stalkerzone.compat.GunAttackGoal(this, StalkerEntity::refillFromFaction));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, StalkerEntity.class, 10, true, false, this::isEnemyNpc));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> hostileToPlayers() || hostileTo(p)));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, ru.stalkerzone.mutant.MutantEntity.class, 10, true, false, m -> !isTrader()));
        // существа других модов, стоящие в логовах вместо наших мутантов
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                m -> !isTrader() && !(m instanceof StalkerEntity) && m.getPersistentData().contains(ru.stalkerzone.mutant.MutantManager.TAG_LAIR)));
    }

    private Simulation sim() {
        MinecraftServer server = this.getServer();
        return server == null ? null : ZoneData.get(server).sim;
    }

    private boolean isEnemyNpc(LivingEntity other) {
        if (isBuilder() || (other instanceof StalkerEntity os && os.isDowned())) return false;
        if (isTrader() || !(other instanceof StalkerEntity o) || o.isTrader() || o.faction() == faction()) return false;
        Simulation sim = sim();
        if (sim == null || faction() >= sim.factions.size()) return false;
        return sim.hostilityTo(sim.factions.get(faction()), o.faction()) >= 0.6;
    }

    /** v1.14: игрок с плохой репутацией у этой группировки — враг. */
    private boolean hostileTo(LivingEntity p) {
        if (isTrader() || !(p instanceof net.minecraft.server.level.ServerPlayer sp)) return false;
        return Reputation.hostile(sp, faction());
    }

    private boolean hostileToPlayers() {
        if (isTrader()) return false;
        Simulation sim = sim();
        if (sim == null || faction() >= sim.factions.size()) return false;
        Faction f = sim.factions.get(faction());
        return f.raider || f.fanatic;
    }

    // ---------------------------------------------------------------- ранения (v1.6)

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isDowned();
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        if (pose == net.minecraft.world.entity.Pose.SLEEPING) return net.minecraft.world.entity.EntityDimensions.scalable(0.9f, 0.5f);
        return super.getDimensions(pose);
    }

    /** Шанс, что смертельно раненый упадёт, а не умрёт: выше у фракций с медиками. Монолит не падает. */
    private double downChance() {
        Simulation sim = sim();
        if (sim == null || faction() >= sim.factions.size() || isTrader()) return 0;
        Faction f = sim.factions.get(faction());
        if (f.fanatic) return 0;
        return sim.people.medics(f.id) > 0 ? 0.5 : 0.3;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || isInvulnerableTo(source)) return super.hurt(source, amount);
        if (isDowned()) return super.hurt(source, Math.max(amount, getHealth() + 1)); // добивают
        if (amount >= getHealth() && random.nextDouble() < downChance()) {
            boolean r = super.hurt(source, Math.max(0, getHealth() - 1));
            if (isAlive()) {
                setHealth(1);
                this.entityData.set(DOWNED, true);
                this.setPose(net.minecraft.world.entity.Pose.SLEEPING);
                this.setTarget(null);
                this.getNavigation().stop();
                downTicks = 600;
            }
            return r;
        }
        return super.hurt(source, amount);
    }

    public void revive(float healthFraction) {
        if (!isDowned()) return;
        this.entityData.set(DOWNED, false);
        this.setPose(net.minecraft.world.entity.Pose.STANDING);
        this.setHealth(Math.max(2, getMaxHealth() * healthFraction));
        downTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !isDowned()) return;
        if (getPose() != net.minecraft.world.entity.Pose.SLEEPING) setPose(net.minecraft.world.entity.Pose.SLEEPING);
        if (--downTicks <= 0) {
            this.entityData.set(DOWNED, false);
            this.kill();
        } else if (downTicks % 100 == 0) {
            this.playSound(net.minecraft.sounds.SoundEvents.PLAYER_HURT, 0.6f, 0.7f);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!this.level().isClientSide && isDowned()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof ru.stalkerzone.item.GoodItem gi && gi.good == ru.stalkerzone.sim.Good.MEDS) {
                held.shrink(1);
                revive(0.5f);
                player.sendSystemMessage(Component.literal("Вы перевязали сталкера, он снова на ногах."));
                // сталкерский кодекс: раненому помогают все
                Simulation simR = sim();
                if (simR != null && player instanceof net.minecraft.server.level.ServerPlayer spR) Reputation.add(spR, simR, faction(), 10, "помогли раненому");
            } else {
                player.sendSystemMessage(Component.literal("Сталкер тяжело ранен. Аптечка в руке и ПКМ, чтобы помочь."));
            }
            return InteractionResult.SUCCESS;
        }
        if (!this.level().isClientSide) {
            Simulation sim = sim();
            if (sim != null && homeNode >= 0 && homeNode < sim.nodes.size()) {
                ItemStack held0 = player.getItemInHand(hand);
                if (isTrader() && player instanceof net.minecraft.server.level.ServerPlayer sp0
                        && held0.getItem() instanceof ru.stalkerzone.item.DataDriveItem && ru.stalkerzone.item.DataDriveItem.hasData(held0)) {
                    String msg = ru.stalkerzone.pda.PdaService.deliverLabData(sp0, ZoneData.get(this.getServer()));
                    sp0.sendSystemMessage(Component.literal(msg));
                    ZoneData.get(this.getServer()).setDirty();
                } else if (isTrader() && player instanceof net.minecraft.server.level.ServerPlayer sp
                        && player.getItemInHand(hand).getItem() instanceof ru.stalkerzone.item.GoodItem) {
                    ZoneData data = ZoneData.get(this.getServer());
                    TradeService.sell(sp, data, sim.nodes.get(homeNode), player.getItemInHand(hand));
                } else if (isTrader() && player instanceof net.minecraft.server.level.ServerPlayer sp2
                        && TradeService.gearUnits(player.getItemInHand(hand)) > 0) {
                    TradeService.sellEquipment(sp2, ZoneData.get(this.getServer()), sim.nodes.get(homeNode), player.getItemInHand(hand));
                } else if (isTrader()) {
                    player.sendSystemMessage(Component.literal("С товаром в руке: ПКМ продаёт весь стак (или костюм, рюкзак). Купить: /zone buy <товар> <число>, снаряжение: /zone buy заря, рюкзак... Ремонт: /zone repair"));
                    for (String line : Report.market(sim, sim.nodes.get(homeNode)).split("\n")) {
                        player.sendSystemMessage(Component.literal(line));
                    }
                } else if (faction() < sim.factions.size()) {
                    Faction f = sim.factions.get(faction());
                    ru.stalkerzone.sim.Person who = personId > 0 ? sim.people.byId(personId) : null;
                    if (who != null) player.sendSystemMessage(Component.literal("«" + f.name + "», " + who.describe(sim)));
                    player.sendSystemMessage(Component.literal("«" + f.name + "»: " + (isBuilder() ? "Чиним, что разнесли. Не мешай." : dialogue(f))));
                }
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    private String dialogue(Faction f) {
        if (f.raider) return "Проходи мимо, пока цел.";
        if (f.scientist) return "Не мешайте, у нас исследования.";
        if (f.fanatic) return "...";
        if (f.military) return "Двигайтесь, здесь нельзя задерживаться.";
        return "Тут неспокойно. Держись подальше от аномалий.";
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide && !isTrader()) {
            Simulation sim = sim();
            if (sim != null) {
                ru.stalkerzone.sim.Person who = personId > 0 ? sim.people.byId(personId) : null;
                if (who != null) {
                    boolean leader = who.role == ru.stalkerzone.sim.Person.Role.LEADER;
                    String killer = source.getEntity() instanceof Player pk ? pk.getGameProfile().getName() : null;
                    sim.people.kill(sim, personId, killer != null ? "убит игроком " + killer : "убит");
                    if (leader && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        ru.stalkerzone.sim.ContractBoard.Reward r = sim.contracts.leaderKilled(sim, faction(), killer);
                        if (r != null) {
                            ru.stalkerzone.pda.PdaService.give(sp, r);
                            sp.sendSystemMessage(Component.literal("Заказ на засаду выполнен: " + ru.stalkerzone.pda.PdaService.describe(r)));
                        }
                    }
                } else {
                    sim.npcKilled(faction());
                }
                if (source.getEntity() instanceof Player) dropLoot(sim);
                if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer spK) Reputation.killed(spK, sim, faction());
                MinecraftServer server = this.getServer();
                if (server != null) ZoneData.get(server).setDirty();
            }
        }
    }

    /** Добыча с убитого игроком сталкера берётся из склада и кассы его фракции. */
    private void dropLoot(Simulation sim) {
        int[] loot = sim.npcLoot(faction(), new java.util.Random());
        ItemStack held = this.getMainHandItem();
        boolean gun = ru.stalkerzone.compat.Tacz.isGun(held);
        ru.stalkerzone.compat.CompatConfig.GunSpec spec = gun ? ru.stalkerzone.compat.CompatConfig.GUNS.get(ru.stalkerzone.compat.Tacz.gunId(held)) : null;
        for (ru.stalkerzone.sim.Good g : ru.stalkerzone.sim.Good.ALL) {
            int q = loot[g.ordinal()];
            if (q <= 0) continue;
            if (spec != null && g.isAmmo()) {
                // патроны с тела — того же калибра, что и оружие убитого, той же выделки
                int rounds = q * ru.stalkerzone.compat.CompatConfig.roundsPerUnit(spec.ammo()) / 3;
                while (rounds > 0) {
                    int c = Math.min(60, rounds);
                    this.spawnAtLocation(ru.stalkerzone.compat.GunQuality.markAmmo(ru.stalkerzone.compat.Tacz.ammo(spec.ammo(), c), g == ru.stalkerzone.sim.Good.AMMO));
                    rounds -= c;
                }
                continue;
            }
            if (g.isGear() && this.random.nextBoolean()) {
                // костюм с тела (изношенный), вместо единицы снаряжения
                ItemStack suit = this.getItemBySlot(EquipmentSlot.CHEST);
                if (suit.getItem() instanceof ru.stalkerzone.gear.SuitItem) {
                    ItemStack drop = suit.copy();
                    drop.setDamageValue(Math.min(drop.getMaxDamage() - 1, drop.getDamageValue() + drop.getMaxDamage() / 4));
                    this.spawnAtLocation(drop);
                    if (q > 1) this.spawnAtLocation(new ItemStack(ru.stalkerzone.item.ModItems.of(g), q - 1));
                    continue;
                }
            }
            if (gun && g.isGear()) {
                // единица снаряжения с тела — это его оружие с остатком магазина
                ItemStack drop = held.copy();
                ru.stalkerzone.compat.Tacz.setAmmoIn(drop, Math.min(ru.stalkerzone.compat.Tacz.ammoIn(drop), 3 + this.random.nextInt(6)));
                this.spawnAtLocation(drop);
                if (q > 1) this.spawnAtLocation(new ItemStack(ru.stalkerzone.item.ModItems.of(g), q - 1));
                continue;
            }
            this.spawnAtLocation(new ItemStack(ru.stalkerzone.item.ModItems.of(g), q));
        }
        for (ItemStack s : ru.stalkerzone.item.ModItems.rubleStacks(loot[Simulation.G])) this.spawnAtLocation(s);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Faction", faction());
        tag.putInt("Role", role());
        tag.putInt("HomeNode", homeNode);
        tag.putInt("HomeX", homeX);
        tag.putInt("HomeY", homeY);
        tag.putInt("HomeZ", homeZ);
        tag.putInt("Person", personId);
        tag.putBoolean("Medic", medic);
        tag.putBoolean("Downed", isDowned());
        tag.putBoolean("Elite", elite);
        tag.putInt("DownTicks", downTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(FACTION, tag.getInt("Faction"));
        this.entityData.set(ROLE, tag.getInt("Role"));
        this.homeNode = tag.getInt("HomeNode");
        this.homeX = tag.getInt("HomeX");
        this.homeY = tag.getInt("HomeY");
        this.homeZ = tag.getInt("HomeZ");
        this.personId = tag.getInt("Person");
        this.medic = tag.getBoolean("Medic");
        this.entityData.set(DOWNED, tag.getBoolean("Downed"));
        this.elite = tag.getBoolean("Elite");
        this.downTicks = tag.getInt("DownTicks");
        if (isTrader()) {
            this.setNoAi(true);
            this.setInvulnerable(true);
            this.restrictTo(new BlockPos(homeX, homeY, homeZ), 4);
        } else {
            this.restrictTo(new BlockPos(homeX, homeY, homeZ), isBuilder() ? 6 : 28);
        }
    }
}
