package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Склад: количество каждого товара + деньги. Все переносы идут через move/moveMoney, чтобы ничего не появлялось из воздуха. */
public final class Stock {
    public final int[] qty = new int[Good.ALL.length];
    public long money;

    public int get(Good g) {
        return qty[g.ordinal()];
    }

    public void add(Good g, int n) {
        qty[g.ordinal()] += n;
    }

    /** Забрать до n единиц, вернуть сколько реально забрали. */
    public int take(Good g, int n) {
        int t = Math.max(0, Math.min(n, qty[g.ordinal()]));
        qty[g.ordinal()] -= t;
        return t;
    }

    public static int move(Stock from, Stock to, Good g, int n) {
        int t = from.take(g, n);
        to.add(g, t);
        return t;
    }

    public static long moveMoney(Stock from, Stock to, long amount) {
        long t = Math.min(Math.max(amount, 0), from.money);
        from.money -= t;
        to.money += t;
        return t;
    }

    public void write(DataOutputStream out) throws IOException {
        for (int q : qty) out.writeInt(q);
        out.writeLong(money);
    }

    public void read(DataInputStream in) throws IOException {
        for (int i = 0; i < qty.length; i++) qty[i] = i < Good.readCount ? in.readInt() : 0;
        money = in.readLong();
    }
}
