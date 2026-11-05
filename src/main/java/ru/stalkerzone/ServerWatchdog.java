package ru.stalkerzone;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * Сторож серверного потока (П-01, открытая проблема 1): если сервер не начинает новый тик дольше {@link #STALL_MS},
 * пишет в logs/latest.log полный стек серверного потока и повторяет каждые {@link #STALL_MS}, пока тик стоит.
 * Так зависание у игрока само оставляет в логе место, где встал сервер. Пауза одиночной игры (Esc) не считается.
 * Отключается ключом "watchdog": false в config/stalkerzone/compat.json.
 */
public final class ServerWatchdog {
    private static final Logger LOG = LogUtils.getLogger();
    static final long STALL_MS = 10_000;

    private static volatile long lastTick;
    /** Клиент одиночной игры на паузе (ставит клиентский тик): тик сервера стоит законно. */
    public static volatile boolean clientPaused;
    private static volatile MinecraftServer server;
    private static volatile Thread thread;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTick(TickEvent.ServerTickEvent e) { lastTick = System.currentTimeMillis(); }

    @SubscribeEvent
    public void onStarted(ServerStartedEvent e) {
        if (!ru.stalkerzone.compat.CompatConfig.watchdog) { LOG.info("[stalkerzone] сторож серверного потока выключен (compat.json)"); return; }
        server = e.getServer();
        lastTick = System.currentTimeMillis();
        Thread t = new Thread(ServerWatchdog::loop, "StalkerZone watchdog");
        t.setDaemon(true);
        thread = t;
        t.start();
        LOG.info("[stalkerzone] сторож серверного потока включён: стек в лог, если тик стоит дольше {} с", STALL_MS / 1000);
    }

    @SubscribeEvent
    public void onStopping(ServerStoppingEvent e) {   // сохранение мира при выходе — долгое, это не зависание
        Thread t = thread;
        thread = null;
        server = null;
        if (t != null) t.interrupt();
    }

    private static void loop() {
        long reported = 0;
        while (thread == Thread.currentThread()) {
            try { Thread.sleep(1000); } catch (InterruptedException ex) { return; }
            MinecraftServer s = server;
            if (s == null || !s.isRunning()) continue;
            long now = System.currentTimeMillis(), stall = now - lastTick;
            if (paused(s)) { lastTick = now; reported = 0; continue; }
            if (stall < STALL_MS) { reported = 0; continue; }
            if (reported != 0 && now - reported < STALL_MS) continue;
            reported = now;
            dump(s, stall);
        }
    }

    /** Одиночная игра на паузе (Esc) — тик стоит законно. */
    private static boolean paused(MinecraftServer s) {
        return clientPaused && !s.isDedicatedServer();
    }

    static void dump(MinecraftServer s, long stall) {
        Thread st = s.getRunningThread();
        StringBuilder b = new StringBuilder();
        b.append("[stalkerzone] СТОРОЖ: сервер не тикает ").append(stall / 1000).append(" с (тик ").append(s.getTickCount())
                .append("). Стек серверного потока «").append(st.getName()).append("» (").append(st.getState()).append("):\n");
        for (StackTraceElement el : st.getStackTrace()) b.append("\tat ").append(el).append('\n');
        // кого ждёт серверный поток: потоки генерации чанков и рабочие — коротко (верх стека)
        for (java.util.Map.Entry<Thread, StackTraceElement[]> en : Thread.getAllStackTraces().entrySet()) {
            Thread t = en.getKey();
            String n = t.getName();
            if (t == st || !(n.startsWith("Worker-") || n.contains("Chunk") || n.contains("IO") || n.contains("Gen"))) continue;
            if (t.getState() == Thread.State.WAITING || t.getState() == Thread.State.TIMED_WAITING) continue;
            b.append("-- ").append(n).append(" (").append(t.getState()).append(")\n");
            StackTraceElement[] tr = en.getValue();
            for (int i = 0; i < Math.min(12, tr.length); i++) b.append("\tat ").append(tr[i]).append('\n');
        }
        LOG.warn(b.toString());
    }
}
