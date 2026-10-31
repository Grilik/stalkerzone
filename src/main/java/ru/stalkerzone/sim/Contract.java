package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Контракт: заказчик кладёт награду в залог при выдаче; при провале или истечении срока залог возвращается. */
public final class Contract {
    public enum Type {
        FETCH_ARTIFACT("Принести артефакты"), SUPPLY("Поставка"), CLEAR_LAIR("Зачистить логово"),
        LAB_DATA("Данные лаборатории"), GUARD_MEETING("Охрана встречи"), AMBUSH_MEETING("Засада на встречу"),
        // v16 (карточка 006): контракты Кордона — сопровождение курьера Торговцев и захват узла
        ESCORT_COURIER("Сопроводить курьера"), CAPTURE_NODE("Выбить врага с узла"),
        // v17 (карточка 021): заказы Бандитов — засада на курьера Торговцев и налёт на чужую ферму.
        // Новые значения строго в конец: тип пишется ординалом, старые сохранения читаются.
        AMBUSH_COURIER("Засада на курьера"), RAID_NODE("Налёт");
        public final String ru;
        Type(String ru) { this.ru = ru; }
    }

    public enum Status {
        OPEN("открыт"), TAKEN("взят"), READY("выполнен, ждёт награды"), DONE("закрыт"), FAILED("провален"), EXPIRED("истёк");
        public final String ru;
        Status(String ru) { this.ru = ru; }
    }

    public static final int NOBODY = -1, PLAYER = -2;

    public final int id;
    public final Type type;
    public final int issuer;
    /** Точка цели (логово, место встречи) или сдачи. */
    public int node;
    public int lab = -1;
    public Good good = Good.ARTIFACT;
    /**
     * Количество товара (FETCH_ARTIFACT, SUPPLY). Особые случаи:
     * AMBUSH_MEETING — host*100+guest, CAPTURE_NODE — исходный владелец узла,
     * ESCORT_COURIER — id фракции-покупателя (куда ведут курьера),
     * AMBUSH_COURIER — id фракции-покупателя курьера, RAID_NODE — исходный владелец фермы.
     */
    public int qty;
    /** Залог из кассы заказчика: учитывается в законе сохранения. */
    public long escrow;
    /** Часть награды товаром из склада заказчика (тоже в залоге). */
    public Good rewardGood = Good.AMMO;
    public int rewardQty;
    /** Надбавка институтов при сдаче данных (не из кассы, выдаётся напрямую игроку). */
    public long bonus;
    public int created, deadline;
    public int difficulty;
    public int taker = NOBODY;
    public String player = "";
    public Status status = Status.OPEN;
    /** Для NPC-исполнителя: день, когда станет ясен исход. */
    public int resolveDay;
    /**
     * Карточка 026: счётчик целей контракта на логово (CLEAR_LAIR) у игрока. {@code lairTotal} — сколько
     * мутантов логова было на момент взятия, {@code lairKilled} — сколько из них убил игрок. У остальных
     * контрактов и у NPC нули. В сохранение пишется отдельной секцией (v18), старые версии читаются без неё.
     */
    public int lairTotal, lairKilled;

    public Contract(int id, Type type, int issuer) {
        this.id = id;
        this.type = type;
        this.issuer = issuer;
    }

    public boolean active() {
        return status == Status.OPEN || status == Status.TAKEN || status == Status.READY;
    }

    public long reward() { return escrow + bonus; }

    public String rewardRu() {
        return (escrow + bonus) + " руб." + (rewardQty > 0 ? " + " + rewardGood.ru.toLowerCase() + " " + rewardQty + " шт." : "");
    }

    public String describe(Simulation s) {
        String who = issuer >= 0 ? s.factions.get(issuer).name : "Институты";
        String what = switch (type) {
            case FETCH_ARTIFACT -> "принести " + (good == Good.ARTIFACT ? "артефакты" : good.ru.toLowerCase())
                    + " (" + qty + " шт.) на «" + s.nodes.get(node).name + "»";
            case SUPPLY -> "доставить " + good.ru.toLowerCase() + " (" + qty + " шт.) на базу «" + s.nodes.get(node).name + "»";
            case CLEAR_LAIR -> {
                Lair l = s.lairAt(node);
                yield "уничтожить логово " + (l != null ? "«" + l.title() + "» " : "") + "у «" + s.nodes.get(node).name + "»";
            }
            case LAB_DATA -> "скачать данные в «" + s.labs.labs.get(lab).name + "» и сдать учёным на «" + s.nodes.get(node).name
                    + "» (премия институтов " + s.labs.labs.get(lab).playerReward() + " руб. за каждую часть)";
            case GUARD_MEETING -> "охранять встречу лидеров на «" + s.nodes.get(node).name + "» в день " + deadline;
            case AMBUSH_MEETING -> "убить лидера на встрече на «" + s.nodes.get(node).name + "» в день " + deadline;
            case ESCORT_COURIER -> "сопроводить курьера Торговцев до базы «" + s.nodes.get(node).name + "»";
            case CAPTURE_NODE -> "выбить " + (qty >= 0 ? "«" + s.factions.get(qty).name + "» " : "врага ")
                    + "с «" + s.nodes.get(node).name + "»";
            case AMBUSH_COURIER -> "устроить засаду на курьера Торговцев, идущего к "
                    + (qty >= 0 ? "«" + s.factions.get(qty).name + "»" : "покупателю") + " (груз должен пропасть)";
            case RAID_NODE -> "разорить «" + s.nodes.get(node).name + "» и увести запасы еды";
        };
        String diff = "★★★★★".substring(0, Math.max(1, Math.min(5, difficulty)));
        return String.format("#%d %s [%s] от «%s»: %s; награда %s; срок до дня %d; %s%s", id, type.ru, diff, who, what, rewardRu(),
                deadline, status.ru, taker == PLAYER ? " (" + player + ")" : taker >= 0 ? " (" + s.factions.get(taker).name + ")" : "");
    }

    void write(DataOutputStream out) throws IOException {
        out.writeInt(id);
        out.writeInt(type.ordinal());
        out.writeInt(issuer);
        out.writeInt(node);
        out.writeInt(lab);
        out.writeInt(good.ordinal());
        out.writeInt(qty);
        out.writeLong(escrow);
        out.writeLong(bonus);
        out.writeInt(rewardGood.ordinal());
        out.writeInt(rewardQty);
        out.writeInt(created);
        out.writeInt(deadline);
        out.writeInt(difficulty);
        out.writeInt(taker);
        out.writeUTF(player);
        out.writeInt(status.ordinal());
        out.writeInt(resolveDay);
    }

    static Contract read(DataInputStream in) throws IOException {
        int id = in.readInt();
        Type t = Type.values()[in.readInt()];
        Contract c = new Contract(id, t, in.readInt());
        c.node = in.readInt();
        c.lab = in.readInt();
        c.good = Good.ALL[in.readInt()];
        c.qty = in.readInt();
        c.escrow = in.readLong();
        c.bonus = in.readLong();
        c.rewardGood = Good.ALL[in.readInt()];
        c.rewardQty = in.readInt();
        c.created = in.readInt();
        c.deadline = in.readInt();
        c.difficulty = in.readInt();
        c.taker = in.readInt();
        c.player = in.readUTF();
        c.status = Status.values()[in.readInt()];
        c.resolveDay = in.readInt();
        return c;
    }
}
