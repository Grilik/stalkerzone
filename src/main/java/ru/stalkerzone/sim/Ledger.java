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

    public void write(DataOutputStream out) throws IOException {
        for (long[] a : new long[][] {created, destroyed, imported, exported}) {
            for (long v : a) out.writeLong(v);
        }
        out.writeLong(moneyMinted);
        out.writeLong(moneyBurned);
    }

    public void read(DataInputStream in) throws IOException {
        for (long[] a : new long[][] {created, destroyed, imported, exported}) {
            for (int i = 0; i < a.length; i++) a[i] = i < Good.readCount ? in.readLong() : 0;
        }
        moneyMinted = in.readLong();
        moneyBurned = in.readLong();
    }
}
