package dev.jensh.createinduction.compat.powergrid;

import dev.jensh.createinduction.content.coil.InductionCoilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Only loaded when Power Grid is installed. */
public final class PowerGridCompat {
    private PowerGridCompat() {}

    public static InductionCoilBlockEntity createCoil(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        return new PowerGridCoilBlockEntity(type, pos, state);
    }
}
