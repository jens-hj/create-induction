package dev.jensh.createinduction.content.commutator;

import dev.jensh.createinduction.ModContent;
import dev.jensh.createinduction.content.slipring.SlipRingBlock;
import dev.jensh.createinduction.content.slipring.SlipRingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * A slip ring cut into segments. Paired with a plain Slip Ring across a turning joint, it feeds the coils attached
 * to it and reverses each one as it turns past the magnets, which is what makes a motor of them.
 */
public class CommutatorBlock extends SlipRingBlock {
    public CommutatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends SlipRingBlockEntity> getBlockEntityType() {
        return ModContent.COMMUTATOR_ENTITY.get();
    }
}
