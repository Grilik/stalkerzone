package ru.stalkerzone.sim;

/**
 * Решение командования. Мозг фракции только предлагает команды, исполняет и проверяет их симуляция:
 * лишнего со склада не возьмёт, правила (лимит караванов, готовность к войне) обойти нельзя.
 */
public sealed interface Command {

    /** Отправить караван к торговцу hub: продать sell, закупить want на сумму до budget. */
    record Trade(int hub, int[] sell, int[] want, int guards, long budget) implements Command {}

    /** Начать наступление на локацию. */
    record Attack(int node) implements Command {}

    /** Приоритеты производства (0..2): патроны, снаряжение, медикаменты. */
    record Production(double ammo, double gear, double meds) implements Command {}

    /** Как фракция ведёт себя при нападении Монолита: принять бой (true) или отойти без боя (false). */
    record Stance(boolean fight) implements Command {}
}
