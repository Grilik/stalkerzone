package ru.stalkerzone.container;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.item.GoodItem;
import ru.stalkerzone.item.RubleItem;
import ru.stalkerzone.sim.Ledger;

import java.util.List;

/** Э1-17: учёт добычи контейнеров мира в Ledger — товары симуляции (worldLoot) и рубли (worldRubles); прочее не учитывается. */
public final class WorldLoot {
    private WorldLoot() {}

    public static void account(MinecraftServer server, List<ItemStack> items) {
        if (server == null) return;
        ZoneData data = ZoneData.get(server);
        Ledger l = data.sim.ledger;
        boolean any = false;
        for (ItemStack st : items) {
            if (st.isEmpty()) continue;
            if (st.getItem() instanceof GoodItem gi) {
                l.worldLoot[gi.good.ordinal()] += (long) st.getCount() * gi.units();
                any = true;
            } else if (st.getItem() instanceof RubleItem r) {
                l.worldRubles += (long) st.getCount() * r.value;
                any = true;
            }
        }
        if (any) data.setDirty();
    }
}
