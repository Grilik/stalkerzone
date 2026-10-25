package ru.stalkerzone.mutant;

import ru.stalkerzone.item.GoodItem;
import ru.stalkerzone.sim.Good;

/** Часть мутанта: торговец берёт её как сырьё для медицины и науки (units единиц «Медикаментов»). */
public class MutantPartItem extends GoodItem {
    public final MutantType type;

    public MutantPartItem(MutantType type) {
        super(Good.MEDS);
        this.type = type;
    }

    @Override
    public int units() { return type.partUnits; }
}
