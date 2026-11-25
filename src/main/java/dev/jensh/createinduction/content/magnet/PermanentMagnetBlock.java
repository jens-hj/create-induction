package dev.jensh.createinduction.content.magnet;

import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import dev.jensh.createinduction.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** An always-on magnet. The north pole is the face it points at. */
public class PermanentMagnetBlock extends WrenchableDirectionalBlock implements IBE<PermanentMagnetBlockEntity> {
    public PermanentMagnetBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction looking = context.getNearestLookingDirection();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) looking = looking.getOpposite();
        return defaultBlockState().setValue(FACING, looking.getOpposite());
    }

    @Override
    public Class<PermanentMagnetBlockEntity> getBlockEntityClass() {
        return PermanentMagnetBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PermanentMagnetBlockEntity> getBlockEntityType() {
        return ModContent.PERMANENT_MAGNET_ENTITY.get();
    }
}
