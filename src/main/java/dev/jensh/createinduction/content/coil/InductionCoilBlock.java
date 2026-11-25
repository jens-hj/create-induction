package dev.jensh.createinduction.content.coil;

import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import dev.jensh.createinduction.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A coil of wire. Magnets moving past its face induce a current; a redstone signal makes it an electromagnet
 * whose north pole is the face it points at.
 */
public class InductionCoilBlock extends WrenchableDirectionalBlock implements IBE<InductionCoilBlockEntity> {
    public InductionCoilBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction looking = context.getNearestLookingDirection();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) looking = looking.getOpposite();
        return defaultBlockState().setValue(FACING, looking.getOpposite());
    }

    @Override
    public Class<InductionCoilBlockEntity> getBlockEntityClass() {
        return InductionCoilBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends InductionCoilBlockEntity> getBlockEntityType() {
        return ModContent.INDUCTION_COIL_ENTITY.get();
    }
}
