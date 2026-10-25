package ru.stalkerzone.sim;

import java.util.Random;

/**
 * Долгий проект «Выжигатель мозгов». Учёные годами изучают пси-излучатель на севере Зоны,
 * затем нанимают сопровождение и пробиваются к комплексу. Прорыв трёхэтапный и с низким шансом
 * (около 10–13%), провал стоит людей и части данных. Успех отключает Выжигатель, открывает дорогу
 * к ЧАЭС и будит Монолит.
 *
 * Деньги и товары здесь тоже подчиняются закону сохранения: гранты чеканятся через Ledger,
 * расходные материалы уничтожаются через Ledger, оплата сопровождения переходит из казны в казну.
 */
public final class ScorcherProject {
    public static final double RESEARCH_GOAL = 100;
    public static final int ATTEMPT_COOLDOWN = 45;
    public static final int ESCORT = 10;
    public static final long ESCORT_FEE = 1500;
    public static final double[] STAGE_ODDS = {0.60, 0.50, 0.45};
    public static final String[] STAGE_NAMES = {"подход через аномалии", "пси-поле", "комплекс Выжигателя"};

    /** Накопленные исследования 0..100. Нужно набрать 100, чтобы отправить экспедицию. */
    public double research;
    /** Пси-технологии (псишлем) 0..100: растут вместе с исследованиями, повышают шанс пройти пси-поле. */
    public double psiTech;
    public int attempts;
    public int lastAttempt = -ATTEMPT_COOLDOWN;
    public int bestStage;
    public boolean disabled;
    /** Знания из X-10 повышают шансы экспедиции. */
    public double labBonus = 1;

    void daily(Simulation sim, Random rng) {
        if (disabled) return;
        Faction sci = sim.scientists();
        if (sci == null || sci.members < 3) return;

        // гранты институтов с Большой земли
        sim.ledger.moneyMinted += 60;
        sci.stock.money += 60;

        if (research < RESEARCH_GOAL) {
            double rate = 0.12 * sci.members / 25.0;
            int parts = sci.stock.take(Good.PARTS, 4);
            sim.ledger.destroyed[Good.PARTS.ordinal()] += parts;
            if (parts < 4) rate *= 0.3;
            if (sci.stock.take(Good.ARTIFACT, 1) > 0) {
                sim.ledger.destroyed[Good.ARTIFACT.ordinal()]++;
                rate += 0.25;
            }
            research = Math.min(RESEARCH_GOAL, research + rate);
            psiTech = Math.min(100, psiTech + rate * 0.5);
        }
        if (research >= RESEARCH_GOAL && sim.day - lastAttempt >= ATTEMPT_COOLDOWN) expedition(sim, rng, sci);
    }

    private void expedition(Simulation sim, Random rng, Faction sci) {
        // по контракту экспедицию сопровождает армия (уже оплачено), иначе нанимают одиночек
        Faction esc = sim.logistics.escortFor(sim);
        boolean contract = esc != null && esc.military;
        if (esc == null || esc.members < 30 || !contract && sci.stock.money < ESCORT_FEE || sim.readiness(esc) < (contract ? 0.6 : 0.7)) return;

        if (!contract) Stock.moveMoney(sci.stock, esc.stock, ESCORT_FEE);
        Arms.spendAmmo(sim, esc.stock, 300, 0.8);
        sim.ledger.destroyed[Good.MEDS.ordinal()] += esc.stock.take(Good.MEDS, 15);
        lastAttempt = sim.day;
        attempts++;
        sim.log("Учёные и «" + esc.name + "» выступили к Выжигателю (попытка " + attempts + ")");

        double techFactor = 0.6 + 0.4 * psiTech / 100.0;
        int stage = 0;
        while (stage < STAGE_ODDS.length) {
            double p = Math.min(0.95, STAGE_ODDS[stage] * (stage == 1 ? techFactor : 1.0) * labBonus);
            if (rng.nextDouble() >= p) break;
            stage++;
        }
        if (stage == STAGE_ODDS.length) {
            disabled = true;
            sim.log("ВЫЖИГАТЕЛЬ ОТКЛЮЧЁН. Экспедиция прорвалась через все этапы");
            sim.activateMonolith();
            return;
        }

        bestStage = Math.max(bestStage, stage);
        int escortLoss = Math.min(ESCORT, ESCORT * (stage + 1) / 3 + rng.nextInt(2));
        int sciLoss = stage == 0 ? 0 : stage == 1 ? 1 + rng.nextInt(2) : 2 + rng.nextInt(3);
        double dataLoss = stage == 0 ? 10 : stage == 1 ? 30 : 50;
        esc.members = Math.max(1, esc.members - escortLoss);
        esc.deaths += escortLoss;
        Arms.loseGear(sim, esc.stock, escortLoss / 2);
        sci.members = Math.max(1, sci.members - sciLoss);
        sci.deaths += sciLoss;
        research = Math.max(0, research - dataLoss);
        sim.log("Прорыв сорван на этапе «" + STAGE_NAMES[stage] + "»: погибло " + escortLoss + " бойцов и "
                + sciLoss + " учёных, потеряно " + (int) dataLoss + "% данных");
    }
}
