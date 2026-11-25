package dev.jensh.createinduction.content.commutator;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.jensh.createinduction.content.coil.CoilPhysics;
import dev.jensh.createinduction.content.coil.InductionCoilBlockEntity;
import dev.jensh.createinduction.content.slipring.SlipRingBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The switching half of a brushed motor. The coils joined to it, directly or through each other, are its armature.
 * While it touches a slip ring on another structure it hands them the energy that ring is given and tells each coil
 * which way to carry current, from where that coil stands relative to the other side.
 *
 * <p>The magnets are assumed to alternate around the axis, with a north pole facing the axis on the other
 * ring's reference side (east for an upright axis), {@code poles} of them in all. A coil then wants its outer face
 * alternately north and south as it passes them: a current that follows {@code sin(pairs * angle)}. Which way
 * that turns the rotor depends on the sign; setting the pole count negative runs the motor the other way.
 */
public class CommutatorBlockEntity extends SlipRingBlockEntity implements InductionCoilBlockEntity.Commutation {
    private static final int MAX_ARMATURE = 32;

    private ScrollValueBehaviour pairs;
    private final List<InductionCoilBlockEntity> armature = new ArrayList<>();

    public CommutatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        pairs = new ScrollValueBehaviour(Component.translatable("create_induction.commutator.poles"), this,
            new CenteredSideValueBoxTransform((state, side) -> side.getAxis() != state.getValue(BlockStateProperties.FACING).getAxis()));
        pairs.between(-8, 8);
        pairs.withFormatter(value -> value == 0 ? "0" : (value < 0 ? "-" : "") + Math.abs(value) * 2);
        pairs.value = 1;
        behaviours.add(pairs);
    }

    /** Pole pairs of the magnets around the armature; negative reverses the motor, zero switches it off. */
    public void setPairs(int value) {
        pairs.setValue(value);
    }

    public int pairs() {
        return pairs.getValue();
    }

    public int armatureSize() {
        return armature.size();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if ((level.getGameTime() + worldPosition.asLong()) % 20 == 0 || armature.removeIf(InductionCoilBlockEntity::isRemoved)) findArmature();
        if (partner() == null) return;
        for (InductionCoilBlockEntity coil : armature) coil.commutate(this);
    }

    /** Every coil reachable from this block through touching coils. */
    private void findArmature() {
        armature.clear();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(worldPosition);
        seen.add(worldPosition);
        while (!open.isEmpty() && armature.size() < MAX_ARMATURE) {
            BlockPos from = open.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = from.relative(side);
                if (!seen.add(next)) continue;
                if (level.getBlockEntity(next) instanceof InductionCoilBlockEntity coil && !coil.isRemoved()) {
                    armature.add(coil);
                    open.add(next);
                }
            }
        }
    }

    /** Energy that crosses the joint goes to the armature, shared out evenly. */
    @Override
    protected int distribute(int amount, boolean simulate) {
        if (armature.isEmpty()) return super.distribute(amount, simulate);
        int left = amount;
        for (int i = 0; i < armature.size() && left > 0; i++) {
            int share = Math.max(1, left / (armature.size() - i));
            left -= armature.get(i).energy().receiveEnergy(share, simulate);
        }
        return amount - left;
    }

    @Override
    public double factor(Vector3dc coilWorld, CoilPhysics.Step step) {
        SlipRingBlockEntity other = partner();
        int polePairs = pairs.getValue();
        if (other == null || other.isRemoved() || polePairs == 0) return 0;

        // Where the coil stands around the axis...
        Vector3d point = new Vector3d(), axis = new Vector3d();
        contact(point, axis);
        Vector3d arm = new Vector3d(coilWorld).sub(point);
        arm.fma(-arm.dot(axis), axis);
        if (arm.lengthSquared() < 1.0e-6) return 0;

        // ...as seen from the side the magnets are on.
        SubLevel otherLevel = Sable.HELPER.getContaining(other);
        if (otherLevel != null) otherLevel.logicalPose().orientation().transformInverse(arm);
        Direction facing = other.getBlockState().getValue(BlockStateProperties.FACING);
        Vector3d along = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Vector3d reference = facing.getAxis() == Direction.Axis.X ? new Vector3d(0, 0, 1) : new Vector3d(1, 0, 0);
        Vector3d across = along.cross(reference, new Vector3d());
        double angle = Math.atan2(arm.dot(across), arm.dot(reference));
        return -Mth.clamp(2 * Math.sin(polePairs * angle), -1, 1);
    }

    @Override
    protected String titleKey() {
        return "create_induction.commutator.title";
    }
}
