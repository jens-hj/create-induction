package dev.jensh.createinduction.content.bearing;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import dev.jensh.createinduction.ModContent;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Turns the blocks on its face into a free-spinning physics rotor and drives it with torque from the shaft on its
 * back. Unlike a servo bearing it charges the kinetic network for the mechanical work it actually delivers.
 */
public class DynamoBearingBlock extends DirectionalKineticBlock implements IBE<DynamoBearingBlockEntity>, BlockSubLevelAssemblyListener {
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    private static final Map<Direction, VoxelShape> BASE = Slabs.slice(0, 11);

    public DynamoBearingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ASSEMBLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(ASSEMBLED));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!player.mayBuild() || player.isShiftKeyDown() || !stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) withBlockEntityDo(level, pos, bearing -> bearing.assembleNextTick = true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) withBlockEntityDo(level, context.getClickedPos(), DynamoBearingBlockEntity::disassemble);
        return super.onWrenched(level.getBlockState(context.getClickedPos()), context);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ASSEMBLED) ? BASE.get(state.getValue(FACING)) : Shapes.block();
    }

    @Override
    public void beforeMove(ServerLevel origin, ServerLevel result, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(origin, oldPos, DynamoBearingBlockEntity::beforeMove);
    }

    @Override
    public void afterMove(ServerLevel origin, ServerLevel result, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(result, newPos, DynamoBearingBlockEntity::linkPlate);
    }

    @Override
    public Class<DynamoBearingBlockEntity> getBlockEntityClass() {
        return DynamoBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DynamoBearingBlockEntity> getBlockEntityType() {
        return ModContent.DYNAMO_BEARING_ENTITY.get();
    }

    static void switchState(Level level, BlockPos pos, BlockState state) {
        KineticBlockEntity.switchToBlockState(level, pos, state);
    }
}
