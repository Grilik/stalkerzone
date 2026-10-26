package ru.stalkerzone.artifact;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Контейнер для артефактов (v1.14): свинцовый держит до четырёх и глушит их фон; фарфоровый — один, но в нём можно
 * нести ведьмин студень. ПКМ контейнером: если во второй руке артефакт — положить, иначе — достать последний.
 */
public class ContainerItem extends Item {
    public final boolean porcelain;
    public final int capacity;

    public ContainerItem(boolean porcelain) {
        super(new Properties().stacksTo(1));
        this.porcelain = porcelain;
        this.capacity = porcelain ? 1 : 4;
    }

    public static ListTag contents(ItemStack s) {
        CompoundTag t = s.getOrCreateTag();
        if (!t.contains("arts")) t.put("arts", new ListTag());
        return t.getList("arts", 8);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack box = p.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(box);
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack art = p.getItemInHand(other);
        ListTag list = contents(box);
        if (art.getItem() instanceof ArtifactItem ai) {
            if (ai.def().caustic && !porcelain) {
                p.displayClientMessage(Component.literal("§cСтудень проест свинец: нужен фарфоровый контейнер."), true);
                return InteractionResultHolder.fail(box);
            }
            if (list.size() >= capacity) {
                p.displayClientMessage(Component.literal("Контейнер полон (" + capacity + ")."), true);
                return InteractionResultHolder.fail(box);
            }
            list.add(StringTag.valueOf(ForgeRegistries.ITEMS.getKey(art.getItem()).toString()));
            art.shrink(1);
            level.playSound(null, p.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.6f, 1.4f);
            p.displayClientMessage(Component.literal("Артефакт в контейнере: " + list.size() + "/" + capacity), true);
        } else if (!list.isEmpty()) {
            String id = list.getString(list.size() - 1);
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            list.remove(list.size() - 1);
            if (it != null && !p.getInventory().add(new ItemStack(it))) p.drop(new ItemStack(it), false);
            level.playSound(null, p.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.6f, 1.4f);
        } else {
            p.displayClientMessage(Component.literal("Пусто. Возьмите артефакт во вторую руку и нажмите ПКМ контейнером."), true);
        }
        return InteractionResultHolder.consume(box);
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> out, TooltipFlag f) {
        out.add(Component.literal(porcelain ? "§7Для ведьмина студня и прочей едкой дряни" : "§7Глушит фон артефактов"));
        ListTag list = s.getTag() == null ? new ListTag() : s.getTag().getList("arts", 8);
        out.add(Component.literal("§7Внутри: " + list.size() + "/" + capacity));
        for (int i = 0; i < list.size(); i++) {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(list.getString(i)));
            if (it != null) out.add(Component.literal("§8 • ").append(it.getDescription()));
        }
    }
}
