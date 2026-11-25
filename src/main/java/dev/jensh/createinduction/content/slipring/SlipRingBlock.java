package dev.jensh.createinduction.content.slipring;

import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import dev.jensh.createinduction.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Half of a sliding electrical contact. Two of them face to face, one on a turning structure and one beside it,
 * pass electricity across the joint the way slip rings and brushes do.
 */
public class SlipRingBlock extends WrenchableDirectionalBlock implements IBE<SlipRingBlockEntity> {
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            SHAPES.put(facing, Shapes.create(new AABB(0, 0, 0, 1, 1, 1).contract(
                facing.getStepX() / 16.0, facing.getStepY() / 16.0, facing.getStepZ() / 16.0)));
        }
    }

    public SlipRingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The contact points away from whatever it was placed on.
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    /** The contact face stops a pixel short, so two rings can turn against each other without binding. */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
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
