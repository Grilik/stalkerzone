package ru.stalkerzone.sim.tactics;

/**
 * Меткость NPC (v1.15): разброс выстрела в градусах. Опытный стрелок в укрытии по неподвижной цели
 * кладёт пули кучно, новичок на бегу в панике мажет. Числа подобраны так, чтобы бой длился, а не
 * кончался за секунду: у мастера в упоре на 30 блоках попадает примерно каждая вторая пуля, у новичка каждая восьмая.
 */
public final class Accuracy {
    private Accuracy() {}

    public record Shot(int skill, double distance, Morale.State morale, boolean targetMoving, boolean targetCrouched,
                       boolean shooterMoving, boolean suppressed, boolean braced, boolean wounded, boolean leaderNear) {}

    /** Разброс в градусах (полуширина конуса). Сломленный не стреляет: бесконечность. */
    public static double spreadDeg(Shot s) {
        if (s.morale() == Morale.State.BROKEN) return Double.POSITIVE_INFINITY;
        int skill = Math.max(0, Math.min(100, s.skill()));
        double sp = 3.6 - skill * 0.03 + Math.max(0, s.distance()) * (0.04 - skill * 0.00025);
        if (s.morale() == Morale.State.SHAKEN) sp *= 1.4;
        if (s.morale() == Morale.State.PANIC) sp *= 2.6;
        if (s.targetMoving()) sp *= 1.25;
        if (s.targetCrouched()) sp *= 1.12;
        if (s.shooterMoving()) sp *= 1.6;
        if (s.suppressed()) sp *= 1.35;
        if (s.wounded()) sp *= 1.3;
        if (s.braced()) sp *= 0.85;
        if (s.leaderNear()) sp *= 0.93;
        return Math.max(0.5, Math.min(22, sp));
    }

    /**
     * Доля попаданий: по горизонтали выстрел уходит в ±spread, по вертикали в ±spread/2 (так стреляет
     * TacticalCombatGoal), цель — прямоугольник width × height блоков. Оценка для тестов и баланса.
     */
    public static double hitChance(double spreadDeg, double distance, double width, double height) {
        if (Double.isInfinite(spreadDeg)) return 0;
        double d = Math.max(0.5, distance);
        double tw = Math.toDegrees(Math.atan2(width / 2, d)), th = Math.toDegrees(Math.atan2(height / 2, d));
        return Math.min(1, tw / spreadDeg) * Math.min(1, th / (spreadDeg / 2));
    }

    /** Время реакции (тики) от того, как увидел цель, до первого выстрела. */
    public static int reactionTicks(int skill, Morale.State m) {
        int t = 26 - Math.max(0, Math.min(100, skill)) / 6;
        if (m == Morale.State.SHAKEN) t += 6;
        if (m == Morale.State.PANIC) t += 14;
        return Math.max(6, t);
    }

    /** Навык рядового по опыту фракции и выучке (элита стреляет лучше). Именные берут свой навык боя. */
    public static int rankAndFileSkill(double factionExp, boolean elite) {
        return (int) Math.max(10, Math.min(90, 22 + Math.min(45, factionExp * 0.35) + (elite ? 22 : 0)));
    }
}
