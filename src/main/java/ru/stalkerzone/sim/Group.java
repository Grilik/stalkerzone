package ru.stalkerzone.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Э2-17 (К10, г-П8): группа внутри фракции — люди, главная база и узлы, склад с кассой, лидер
 * из существующего ростера и характер: агрессия, тяга к расширению, дисциплина, сплочённость,
 * осторожность, мораль, влияние лидера (все 0..1).
 *
 * <p>Узлом владеет группа ({@link #nodes}); владелец-фракция выводится: {@code Node.owner == faction}.
 * Численность фракции — сумма по группам ({@link Faction#syncGroupMembers}), переход человека
 * между группами — событие ({@link Simulation#transferPerson}). Раскол после гибели лидера — Э7-02,
 * единый реестр людей (PersonId) — Э6-01.
 *
 * <p>Вольные сталкеры — группа без фракции ({@code faction == Person.NONE}), хаб — деревня новичков;
 * членами считаются люди пула {@link People#freeStalkers()}, узлами они не владеют.
 *
 * <p>Склад и касса группы начинаются пустыми: оперативный запас фракции остаётся в {@link Faction#stock},
 * поведение ежедневных циклов не меняется. Наполнение склада группы — события ведущих и зависимых
 * групп (Э2-21); товары и деньги группы учитываются в {@link Simulation#audit()}.
 */
public final class Group {
    public final String id;
    public String name;
    /** Фракция группы; {@link Person#NONE} — вольные сталкеры (без группировки). */
    public final int faction;
    /** Главная база (или хаб) группы. Узел может быть ничейным: группа живёт там, но не владеет им. */
    public int baseNode;
    /** Узлы, которыми владеет группа; владелец-фракция выводится ({@code Node.owner == faction}). */
    public final List<Integer> nodes = new ArrayList<>();
    /** Склад и касса группы (деньги — {@link Stock#money}). */
    public final Stock stock = new Stock();
    public int members;
    /** Лидер из существующего ростера; назначается симуляцией, сохраняется как id. */
    public Person leader;

    /** Агрессия 0..1. */
    public double aggression;
    /** Тяга к расширению 0..1. */
    public double expansion;
    /** Дисциплина 0..1. */
    public double discipline;
    /** Сплочённость 0..1. */
    public double cohesion;
    /** Осторожность 0..1. */
    public double caution;
    /** Мораль 0..1. */
    public double morale;
    /** Влияние лидера 0..1. */
    public double leaderInfluence;

    public Group(String id, String name, int faction, int baseNode, int members) {
        this.id = id;
        this.name = name;
        this.faction = faction;
        this.baseNode = baseNode;
        this.members = members;
    }

    /** Вольные сталкеры: группа без фракции. */
    public boolean isFree() { return faction == Person.NONE; }

    /** Параметры группы одним вызовом (сценарий). */
    public Group traits(double aggression, double expansion, double discipline, double cohesion,
                        double caution, double morale, double leaderInfluence) {
        this.aggression = aggression;
        this.expansion = expansion;
        this.discipline = discipline;
        this.cohesion = cohesion;
        this.caution = caution;
        this.morale = morale;
        this.leaderInfluence = leaderInfluence;
        return this;
    }

    /** Одна группа на фракцию — режим остальных сценариев до переноса (г-П8), поведение не меняется. */
    public static Group defaultFor(Faction f) {
        Group g = new Group("faction-" + f.id, f.name, f.id, f.baseNode, f.members);
        return g.traits(f.aggression, f.raider ? 0.7 : 0.4, f.military ? 0.8 : 0.5, 0.6,
                Math.max(0, Math.min(1, 1 - f.aggression)), 0.6, 0.5);
    }
}