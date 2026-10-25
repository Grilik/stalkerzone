package ru.stalkerzone.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Накопитель для данных лабораторий: флешка (лаборатории 1–3 уровня) или переносной диск (X-10, X-2).
 * Записанные данные хранятся в NBT: лаборатория, номер терминала, цикл исследований.
 */
public class DataDriveItem extends Item {
    public final boolean disk;

    public DataDriveItem(boolean disk) {
        super(new Item.Properties().stacksTo(1));
        this.disk = disk;
    }

    public static boolean hasData(ItemStack s) {
        return s.hasTag() && s.getTag().contains("LabData");
    }

    public static int lab(ItemStack s) { return s.getTag().getCompound("LabData").getInt("Lab"); }
    public static int part(ItemStack s) { return s.getTag().getCompound("LabData").getInt("Part"); }
    public static int cycle(ItemStack s) { return s.getTag().getCompound("LabData").getInt("Cycle"); }

    public static void write(ItemStack s, int lab, int part, int cycle, String labName) {
        CompoundTag d = new CompoundTag();
        d.putInt("Lab", lab);
        d.putInt("Part", part);
        d.putInt("Cycle", cycle);
        d.putString("Name", labName);
        s.getOrCreateTag().put("LabData", d);
    }

    @Override
    public Component getName(ItemStack s) {
        Component base = super.getName(s);
        if (!hasData(s)) return base;
        return Component.literal(base.getString() + " (данные: " + s.getTag().getCompound("LabData").getString("Name") + ", терминал "
                + (part(s) + 1) + ")");
    }

    @Override
    public boolean isFoil(ItemStack s) { return hasData(s); }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> out, TooltipFlag flag) {
        if (hasData(s)) out.add(Component.literal("Сдать учёным на Янтаре: ПКМ по Сахарову или кнопка в КПК"));
        else out.add(Component.literal(disk ? "Вмещает данные любой лаборатории" : "Вмещает данные лабораторий до X-16 включительно"));
    }
}
