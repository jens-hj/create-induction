package dev.jensh.createinduction.content.bearing;

import com.simibubi.create.foundation.block.IBE;
import dev.jensh.createinduction.ModContent;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/** The face of an assembled Dynamo Bearing. It rides on the rotor and is where the bearing grips it. */
public class DynamoPlateBlock extends DirectionalBlock implements IBE<DynamoPlateBlockEntity>, BlockSubLevelAssemblyListener {
    public static final MapCodec<DynamoPlateBlock> CODEC = simpleCodec(DynamoPlateBlock::new);
    private static final Map<Direction, VoxelShape> PLATE = Slabs.slice(12, 16);

    public DynamoPlateBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PLATE.get(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!player.mayBuild() || player.isShiftKeyDown() || !stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) withBlockEntityDo(level, pos, DynamoPlateBlockEntity::toggleParent);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModContent.DYNAMO_BEARING_ITEM.get());
    }

    @Override
    public void beforeMove(ServerLevel origin, ServerLevel result, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(origin, oldPos, DynamoPlateBlockEntity::silence);
    }

    @Override
    public void afterMove(ServerLevel origin, ServerLevel result, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(result, newPos, DynamoPlateBlockEntity::relink);
    }

    @Override
    public Class<DynamoPlateBlockEntity> getBlockEntityClass() {
        return DynamoPlateBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DynamoPlateBlockEntity> getBlockEntityType() {
        return ModContent.DYNAMO_BEARING_PLATE_ENTITY.get();
    }
}
