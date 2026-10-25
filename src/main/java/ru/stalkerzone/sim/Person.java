package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Именной сталкер: лидеры, заместители, командиры отрядов и специалисты. Рядовые бойцы считаются числом
 * (Faction.members), именные входят в это число. Характер: храбрость, жадность, преданность (−1..1).
 */
public final class Person {
    public static final String[] RANKS = {"Новичок", "Опытный", "Ветеран", "Мастер"};
    public static final double[] RANK_XP = {0, 30, 90, 200};

    public enum Cls {
        ASSAULT("Штурмовик"), SNIPER("Снайпер"), MEDIC("Медик"), DOCTOR("Врач"), TECH("Техник"), SCOUT("Разведчик");
        public final String ru;
        Cls(String ru) { this.ru = ru; }
    }

    public enum Role {
        MEMBER("боец"), SPECIALIST("специалист"), SQUAD_LEADER("командир отряда"), DEPUTY("заместитель"), LEADER("лидер");
        public final String ru;
        Role(String ru) { this.ru = ru; }
    }

    public final int id;
    public String name;
    public int faction;
    public Cls cls;
    public Role role = Role.MEMBER;
    /** Навыки 0..100. */
    public int combat, medicine, tech, scouting;
    public double bravery, greed, loyalty;
    public double xp;
    public double stress;
    /** Дней до выздоровления после ранения (0 здоров). */
    public int injury;
    public int joined;
    public int kills;

    public Person(int id) { this.id = id; }

    public int rank() {
        int r = 0;
        for (int i = 0; i < RANK_XP.length; i++) if (xp >= RANK_XP[i]) r = i;
        return r;
    }

    public String rankRu() { return RANKS[rank()]; }

    /** Насколько сталкер хорош как командир: опыт, навык боя, преданность. */
    public double command() {
        return rank() * 20 + combat * 0.5 + loyalty * 15 + bravery * 10;
    }

    public String character() {
        StringBuilder sb = new StringBuilder();
        sb.append(bravery > 0.4 ? "храбрый" : bravery < -0.4 ? "осторожный" : "уравновешенный");
        sb.append(", ").append(greed > 0.4 ? "жадный" : greed < -0.4 ? "бескорыстный" : "расчётливый");
        sb.append(", ").append(loyalty > 0.4 ? "преданный" : loyalty < -0.4 ? "своевольный" : "надёжный");
        return sb.toString();
    }

    public String describe(Simulation sim) {
        return String.format("%s (%s, %s, %s) %s; бой %d, медицина %d, техника %d, разведка %d; стресс %.0f%s",
                name, rankRu(), cls.ru, role.ru, character(), combat, medicine, tech, scouting, stress,
                injury > 0 ? ", ранен (" + injury + " дн.)" : "");
    }

    void write(DataOutputStream out) throws IOException {
        out.writeInt(id);
        out.writeUTF(name);
        out.writeInt(faction);
        out.writeInt(cls.ordinal());
        out.writeInt(role.ordinal());
        out.writeInt(combat);
        out.writeInt(medicine);
        out.writeInt(tech);
        out.writeInt(scouting);
        out.writeDouble(bravery);
        out.writeDouble(greed);
        out.writeDouble(loyalty);
        out.writeDouble(xp);
        out.writeDouble(stress);
        out.writeInt(injury);
        out.writeInt(joined);
        out.writeInt(kills);
    }

    static Person read(DataInputStream in) throws IOException {
        Person p = new Person(in.readInt());
        p.name = in.readUTF();
        p.faction = in.readInt();
        p.cls = Cls.values()[in.readInt()];
        p.role = Role.values()[in.readInt()];
        p.combat = in.readInt();
        p.medicine = in.readInt();
        p.tech = in.readInt();
        p.scouting = in.readInt();
        p.bravery = in.readDouble();
        p.greed = in.readDouble();
        p.loyalty = in.readDouble();
        p.xp = in.readDouble();
        p.stress = in.readDouble();
        p.injury = in.readInt();
        p.joined = in.readInt();
        p.kills = in.readInt();
        return p;
    }
}
