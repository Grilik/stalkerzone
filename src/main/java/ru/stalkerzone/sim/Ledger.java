package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Учёт границ системы. Единственные места, где товары и деньги появляются или исчезают:
 * created (добыча, фермы, крафт), destroyed (еда, патроны, потери), imported / exported (Большая земля).
 * Инвариант: сумма по всем складам = начальная + created + imported - destroyed - exported.
 */
public final class Ledger {
    public final long[] created = new long[Good.ALL.length];
    public final long[] destroyed = new long[Good.ALL.length];
    public final long[] imported = new long[Good.ALL.length];
    public final long[] exported = new long[Good.ALL.length];
    /** Деньги, пришедшие в Зону (экспорт артефактов и излишков). */
    public long moneyMinted;
    /** Деньги, ушедшие из Зоны (импорт, плата за вербовку). */
    public long moneyBurned;
    /**
     * Э1-17: приход из мира — добыча контейнеров мира (генерируется один раз, при первом открытии ящика игроком).
     * Попадает к игроку, а не на склады симуляции, поэтому в инвариант не входит; продажа торговцу — как обычно, imported.
     */
    public final long[] worldLoot = new long[Good.ALL.length];
    /** Э1-17: рубли (физические) из контейнеров мира. */
    public long worldRubles;
    /** Э8-10 (К16): книга мутантов — сколько родилось и погибло за историю мира. Отдельная книга: в инвариант audit() не входит. */
    public long mutantsBorn;
    public long mutantsDied;

    public void write(DataOutputStream out) throws IOException {
        for (long[] a : new long[][] {created, destroyed, imported, exported}) {
            for (long v : a) out.writeLong(v);
        }
        out.writeLong(moneyMinted);
        out.writeLong(moneyBurned);
        for (long v : worldLoot) out.writeLong(v); // v23
        out.writeLong(worldRubles);
        out.writeLong(mutantsBorn); // v24
        out.writeLong(mutantsDied);
    }

    public void read(DataInputStream in) throws IOException { read(in, Simulation.SAVE_VERSION); }

    public void read(DataInputStream in, int version) throws IOException {
        for (long[] a : new long[][] {created, destroyed, imported, exported}) {
            for (int i = 0; i < a.length; i++) a[i] = i < Good.readCount ? in.readLong() : 0;
        }
        moneyMinted = in.readLong();
        moneyBurned = in.readLong();
        if (version >= 23) {
            for (int i = 0; i < worldLoot.length; i++) worldLoot[i] = i < Good.readCount ? in.readLong() : 0;
            worldRubles = in.readLong();
        }
        if (version >= 24) {
            mutantsBorn = in.readLong();
            mutantsDied = in.readLong();
        }
    }
}
