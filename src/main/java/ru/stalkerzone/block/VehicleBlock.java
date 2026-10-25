package ru.stalkerzone.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Брошенная техника одной OBJ-моделью: краска (8 цветов) и повреждения (0 целая, 1 разбитая, 2 сгоревшая). */
public class VehicleBlock extends ObjPropBlock {
    public enum Paint implements StringRepresentable {
        RED, WHITE, BLUE, CREAM, GREEN, BEIGE, ORANGE, OLIVE;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static final EnumProperty<Paint> PAINT = EnumProperty.create("paint", Paint.class);
    public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 2);

    public VehicleBlock(Properties p, GeneratedProps.Vehicle v) {
        super(p, new double[] {0, 0, 0, 16, 16, 16}, v.cells());
        Paint def = Paint.valueOf(v.paint().toUpperCase(java.util.Locale.ROOT));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.EAST)
                .setValue(PAINT, def).setValue(DAMAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PAINT, DAMAGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return super.getStateForPlacement(ctx);
    }
}
