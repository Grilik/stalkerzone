package ru.stalkerzone.client;

import net.minecraft.client.Minecraft;

/** Клиент: принять снимок КПК, открыть экран или обновить открытый. */
public final class ClientPda {
    private ClientPda() {}

    public static void receive(String text, boolean open) {
        PdaModel model = PdaModel.parse(text);
        PdaMapCache.observe(model.minX, model.minZ);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PdaScreen s) s.update(model);
        else if (open) mc.setScreen(new PdaScreen(model));
    }
}
