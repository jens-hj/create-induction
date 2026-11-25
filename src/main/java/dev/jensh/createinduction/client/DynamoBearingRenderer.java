package dev.jensh.createinduction.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.jensh.createinduction.content.bearing.DynamoBearingBlock;
import dev.jensh.createinduction.content.bearing.DynamoBearingBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Draws the spinning input shaft on the back of the bearing. */
public class DynamoBearingRenderer extends SafeBlockEntityRenderer<DynamoBearingBlockEntity> {
    public DynamoBearingRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(DynamoBearingBlockEntity bearing, float partialTicks, PoseStack poseStack,
                              MultiBufferSource bufferSource, int light, int overlay) {
        BlockState state = bearing.getBlockState();
        Direction back = state.getValue(DynamoBearingBlock.FACING).getOpposite();
        SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, back);
        KineticBlockEntityRenderer.standardKineticRotationTransform(shaft, bearing, light)
            .renderInto(poseStack, bufferSource.getBuffer(RenderType.solid()));
    }
}
