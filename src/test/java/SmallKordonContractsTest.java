import ru.stalkerzone.sim.*;

/**
 * Карточка 050: контракты узлов «Малого Кордона» — одиночки заказывают зачистку логова Л3 (узел 21)
 * и доставку припасов на заставу О7 (SUPPLY, узел 19); оплата из кассы заказчика через залог (аудит Ledger сходится);
 * взятый игроком контракт на Л3 NPC не отбирают, выплата за уничтоженное логово идёт игроку.
 */
public class SmallKordonContractsTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        for (long seed = 1; seed <= 3; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            boolean lair = false, supply = false, audit = true;
            for (int d = 0; d < 45; d++) {
                s.step();
                for (Contract c : s.contracts.list) {
                    if (c.type == Contract.Type.CLEAR_LAIR && c.node == 21 && c.issuer == ZoneScenario.LONERS) lair = true;
                    if (c.type == Contract.Type.SUPPLY && c.node == 19 && c.issuer == ZoneScenario.LONERS && c.qty > 0) supply = true;
                }
                if (!s.audit().isEmpty()) audit = false;
            }
            ck(lair, "сид " + seed + ": одиночки заказали зачистку логова Л3 (узел 21)");
            ck(supply, "сид " + seed + ": одиночки заказали доставку на заставу О7 (SUPPLY, узел 19)");
            ck(audit, "сид " + seed + ": 45 дней — аудит Ledger сходится");
        }

        // игрок берёт контракт на Л3: NPC не отбирают, выплата — игроку
        Simulation s = Scenarios.create("kordon", 2);
        Contract c = null;
        for (int d = 0; d < 45 && c == null; d++) {
            s.step();
            for (Contract x : s.contracts.list)
                if (x.type == Contract.Type.CLEAR_LAIR && x.node == 21 && x.status == Contract.Status.OPEN) { c = x; break; }
        }
        ck(c != null, "открытый контракт на Л3 найден");
        if (c != null) {
            ck(c.escrow > 0, "награда за Л3 в залоге у доски: " + c.escrow);
            ck(s.contracts.take(s, c.id, "Тестер") == null && c.taker == Contract.PLAYER, "игрок взял контракт на Л3");
            boolean stolen = false;
            for (int d = 0; d < 5; d++) { s.step(); if (c.taker != Contract.PLAYER) stolen = true; }
            ck(!stolen, "5 дней: NPC не забрали контракт игрока на Л3");
            if (s.lairAt(21) != null && c.status == Contract.Status.TAKEN) {
                ContractBoard.Reward r = s.contracts.lairDestroyed(s, 21, "Тестер");
                ck(r != null && r.money() > 0, "логово Л3 уничтожено игроком — выплата игроку");
            }
            ck(s.audit().isEmpty(), "аудит после контракта на Л3 сходится");
        }
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
