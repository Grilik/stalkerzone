package ru.stalkerzone.sim;

/** Характер и идеология фракции: веса, по которым командование оценивает цели. Значения примерно 0..1. */
public final class Doctrine {
    /** Насколько фракция ценит землю и ресурсы захваченных точек. */
    public final double expansion;
    /** Насколько долго и сильно помнит обиды. */
    public final double vengeance;
    /** Осторожность: требуемая готовность, соотношение сил, время накопления. */
    public final double prudence;
    /** Склонность зарабатывать торговлей, а не войной. */
    public final double mercantile;
    /** Рвение в борьбе с бандитами и мутантами (идея порядка). */
    public final double orderZeal;
    /** Готовность принять бой с превосходящим врагом (Монолитом) вместо отхода. */
    public final double courage;

    public Doctrine(double expansion, double vengeance, double prudence, double mercantile, double orderZeal, double courage) {
        this.expansion = expansion;
        this.vengeance = vengeance;
        this.prudence = prudence;
        this.mercantile = mercantile;
        this.orderZeal = orderZeal;
        this.courage = courage;
    }
}
