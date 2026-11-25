package dev.jensh.createinduction.content.slipring;

import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import dev.jensh.createinduction.ModContent;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Half of a sliding electrical contact. Two of them face to face, one on a turning structure and one beside it,
 * pass electricity across the joint the way slip rings and brushes do.
 */
public class SlipRingBlock extends WrenchableDirectionalBlock implements IBE<SlipRingBlockEntity> {
    public SlipRingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The contact points away from whatever it was placed on.
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public Class<SlipRingBlockEntity> getBlockEntityClass() {
        return SlipRingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SlipRingBlockEntity> getBlockEntityType() {
        return ModContent.SLIP_RING_ENTITY.get();
    }
}
