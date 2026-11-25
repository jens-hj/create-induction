package dev.jensh.createinduction.content.bearing;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.jensh.createinduction.Config;
import dev.jensh.createinduction.ModContent;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public class DynamoBearingBlockEntity extends KineticBlockEntity implements IDisplayAssemblyExceptions, BlockEntitySubLevelActor {
    private static final double TICK = 0.05;
    private static final double RPM_TO_RADIANS = Math.PI / 30.0;

    public boolean assembleNextTick;
    private @Nullable AssemblyException lastException;
    private @Nullable UUID subLevelId;
    private @Nullable BlockPos platePos;
    private @Nullable RotaryConstraintHandle constraint;
    /** Set while Sable is moving this block into another level, so removal does not tear the rotor off. */
    private boolean moving;

    /** Mechanical work done on the rotor during the current tick [physics units]. */
    private double work;
    private double deliveredWatts;
    /** Everything delivered since this block entity loaded [J]. */
    private double totalWork;
    private double impact = Double.NaN;
    private int stressCooldown;
    /** Rotor speed relative to the bearing [RPM], for the goggles. */
    private float rotorSpeed;
    private double lastRelativeSpin;

    public DynamoBearingBlockEntity(BlockPos pos, BlockState state) {
        this(ModContent.DYNAMO_BEARING_ENTITY.get(), pos, state);
    }

    public DynamoBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isAssembled() {
        return getBlockState().getValue(DynamoBearingBlock.ASSEMBLED);
    }

    public float rotorSpeed() {
        return rotorSpeed;
    }

    public double deliveredWatts() {
        return deliveredWatts;
    }

    public double totalWork() {
        return totalWork;
    }

    public @Nullable UUID subLevelId() {
        return subLevelId;
    }

    public @Nullable BlockPos platePos() {
        return platePos;
    }

    private Direction facing() {
        return getBlockState().getValue(DynamoBearingBlock.FACING);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        if (assembleNextTick) {
            assembleNextTick = false;
            if (isAssembled()) disassemble();
            else assemble();
        }
        keepConstraint();
        updateStress();
    }

    // --- kinetics --------------------------------------------------------------------------------------------

    @Override
    public float calculateStressApplied() {
        float applied = (float) (Double.isNaN(impact) ? Config.BEARING_BASE_IMPACT.get() : impact);
        this.lastStressApplied = applied;
        return applied;
    }

    private void updateStress() {
        double watts = work / TICK * Config.WATTS_PER_PHYSICS_POWER.get();
        totalWork += work * Config.WATTS_PER_PHYSICS_POWER.get();
        work = 0;
        deliveredWatts += (watts - deliveredWatts) * 0.2;
        if (deliveredWatts < 0.01) deliveredWatts = 0;
        rotorSpeed = (float) (lastRelativeSpin / RPM_TO_RADIANS);

        double shaftSpeed = Math.abs(getTheoreticalSpeed());
        double wanted = Config.BEARING_BASE_IMPACT.get()
            + (shaftSpeed > 0 ? deliveredWatts * Config.SU_PER_WATT.get() / shaftSpeed : 0);
        if (--stressCooldown > 0) return;
        if (!Double.isNaN(impact) && Math.abs(wanted - impact) <= 0.05 * impact + 0.05) return;
        impact = wanted;
        stressCooldown = 10;
        if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        sendData();
    }

    /**
     * Called every physics sub-step by the plate riding on the rotor: push the rotor towards the shaft's speed
     * with a limited torque and note the work done.
     */
    void drive(ServerSubLevel rotor, RigidBodyHandle rotorHandle, double timeStep) {
        if (level == null || constraint == null || !constraint.isValid()) return;

        Vec3i normal = facing().getNormal();
        Vector3d parentAxis = new Vector3d(normal.getX(), normal.getY(), normal.getZ());
        Vector3d worldAxis = new Vector3d(parentAxis);
        Vector3d spin = rotorHandle.getAngularVelocity(new Vector3d());
        RigidBodyHandle parentHandle = null;
        if (Sable.HELPER.getContaining(this) instanceof ServerSubLevel parent) {
            parent.logicalPose().orientation().transform(worldAxis);
            parentHandle = RigidBodyHandle.of(parent);
            if (parentHandle != null) spin.sub(parentHandle.getAngularVelocity(new Vector3d()));
        }
        double relativeSpin = spin.dot(worldAxis);
        lastRelativeSpin = relativeSpin;

        float speed = getSpeed();
        if (speed == 0) return; // no input or overstressed: let the rotor coast

        double target = speed * RPM_TO_RADIANS * (facing().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1);
        Vector3d rotorAxis = rotor.logicalPose().orientation().transformInverse(worldAxis, new Vector3d());
        double inertia = Math.max(1.0, rotor.getMassTracker().getInertiaTensor().transform(rotorAxis, new Vector3d()).dot(rotorAxis));
        double limit = Math.min(Config.BEARING_MAX_TORQUE.get(),
            Config.BEARING_MAX_POWER.get() / Config.WATTS_PER_PHYSICS_POWER.get() / Math.max(1.0, Math.abs(relativeSpin)));
        double torque = Mth.clamp(Config.BEARING_GAIN.get() * inertia * (target - relativeSpin), -limit, limit);

        rotorHandle.applyTorqueImpulse(rotorAxis.mul(torque * timeStep));
        if (parentHandle != null) parentHandle.applyTorqueImpulse(parentAxis.mul(-torque * timeStep));
        work += Math.max(0, torque * relativeSpin) * timeStep;
    }

    // --- assembly --------------------------------------------------------------------------------------------

    public void assemble() {
        if (!(level instanceof ServerLevel)) return;
        BlockPos pos = getBlockPos();
        Direction facing = facing();
        SimAssemblyHelper.AssemblyResult result;
        try {
            result = SimAssemblyHelper.assembleFromSingleBlock(level, pos, pos.relative(facing), false, false);
            lastException = null;
        } catch (AssemblyException e) {
            lastException = e;
            sendData();
            return;
        }
        if (result == null || !(result.subLevel() instanceof ServerSubLevel rotor)) {
            sendData();
            return;
        }

        DynamoBearingBlock.switchState(level, pos, getBlockState().setValue(DynamoBearingBlock.ASSEMBLED, true));
        BlockPos plate = pos.offset(result.offset());
        level.setBlockAndUpdate(plate, ModContent.DYNAMO_BEARING_PLATE.get().defaultBlockState()
            .setValue(DynamoPlateBlock.FACING, facing));
        if (level.getBlockEntity(pos) instanceof DynamoBearingBlockEntity self) {
            self.subLevelId = rotor.getUniqueId();
            self.platePos = plate;
            self.linkPlate();
            self.attach(rotor);
            self.setChanged();
            self.sendData();
        }
    }

    public void disassemble() {
        if (level == null || isRemoved() || !isAssembled()) return;
        detach();
        SubLevel rotor = subLevelId == null ? null : SubLevelContainer.getContainer(level).getSubLevel(subLevelId);
        BlockPos plate = platePos;
        if (plate != null) {
            removePlate();
            if (rotor != null && !rotor.isRemoved() && rotor != Sable.HELPER.getContaining(this)) {
                SimAssemblyHelper.disassembleSubLevel(level, rotor, plate, getBlockPos(), Rotation.NONE, true);
            }
        }
        forget();
    }

    /** The plate was broken or lost: let go of the rotor without touching its blocks. */
    void onPlateLost() {
        if (level == null || isRemoved()) return;
        detach();
        forget();
    }

    private void forget() {
        subLevelId = null;
        platePos = null;
        lastRelativeSpin = 0;
        if (isAssembled()) {
            DynamoBearingBlock.switchState(level, getBlockPos(), getBlockState().setValue(DynamoBearingBlock.ASSEMBLED, false));
        }
        if (level.getBlockEntity(getBlockPos()) instanceof DynamoBearingBlockEntity self) {
            self.subLevelId = null;
            self.platePos = null;
            self.setChanged();
            self.sendData();
        }
    }

    private void removePlate() {
        if (platePos == null || level == null) return;
        if (!level.getBlockState(platePos).is(ModContent.DYNAMO_BEARING_PLATE.get())) return;
        if (level.getBlockEntity(platePos) instanceof DynamoPlateBlockEntity plate) plate.silence();
        level.setBlock(platePos, Blocks.AIR.defaultBlockState(), 2);
    }

    /** Points the plate back at this bearing; needed after either of them has been moved. */
    void linkPlate() {
        if (level == null || platePos == null) return;
        if (level.getBlockEntity(platePos) instanceof DynamoPlateBlockEntity plate) plate.setParent(this);
    }

    /** The plate ended up somewhere else, e.g. after its sub-level was split or merged. */
    void plateMoved(BlockPos newPos, @Nullable ServerSubLevel newRotor) {
        platePos = newPos;
        subLevelId = newRotor == null ? null : newRotor.getUniqueId();
        detach();
        if (newRotor != null) attach(newRotor);
        setChanged();
    }

    private void keepConstraint() {
        if (constraint != null && !constraint.isValid()) constraint = null;
        if (constraint != null || subLevelId == null || platePos == null) return;
        if (SubLevelContainer.getContainer(level).getSubLevel(subLevelId) instanceof ServerSubLevel rotor && !rotor.isRemoved()) {
            linkPlate();
            attach(rotor);
        }
    }

    private void attach(ServerSubLevel rotor) {
        if (!(level instanceof ServerLevel serverLevel) || platePos == null) return;
        BlockState plateState = level.getBlockState(platePos);
        if (!plateState.is(ModContent.DYNAMO_BEARING_PLATE.get())) return;
        ServerSubLevel parent = Sable.HELPER.getContaining(this) instanceof ServerSubLevel server ? server : null;
        if (parent == rotor) return;

        Direction facing = facing();
        Direction plateFacing = plateState.getValue(DynamoPlateBlock.FACING);
        Vec3 anchor = getBlockPos().relative(facing).getCenter();
        Vec3 plateAnchor = platePos.relative(plateFacing).getCenter();
        Vector3d normal = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Vector3d plateNormal = new Vector3d(plateFacing.getStepX(), plateFacing.getStepY(), plateFacing.getStepZ());
        RotaryConstraintConfiguration configuration = new RotaryConstraintConfiguration(
            new Vector3d(anchor.x, anchor.y, anchor.z),
            new Vector3d(plateAnchor.x, plateAnchor.y, plateAnchor.z).fma(-0.001, plateNormal),
            normal, plateNormal);

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) return;
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        detach();
        constraint = pipeline.addConstraint(parent, rotor, configuration);
        double friction = Config.BEARING_FRICTION.get();
        // A motor with neither stiffness nor damping stalls the joint, so only add one when there is friction.
        if (constraint != null && friction > 0) {
            constraint.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, 0.0, 0.0, friction, false, 0.0);
        }
    }

    private void detach() {
        if (constraint != null) {
            if (constraint.isValid()) constraint.remove();
            constraint = null;
        }
    }

    void beforeMove() {
        moving = true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        detach();
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide && !moving) removePlate();
        super.remove();
    }

    @Override
    public @Nullable Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        if (level == null || subLevelId == null) return null;
        SubLevel rotor = SubLevelContainer.getContainer(level).getSubLevel(subLevelId);
        return rotor == null ? null : List.of(rotor);
    }

    // --- data ------------------------------------------------------------------------------------------------

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (subLevelId != null) compound.putUUID("SubLevelID", subLevelId);
        if (platePos != null) compound.put("Plate", NbtUtils.writeBlockPos(platePos));
        AssemblyException.write(compound, registries, lastException);
        if (clientPacket) {
            compound.putFloat("RotorSpeed", rotorSpeed);
            compound.putDouble("Delivered", deliveredWatts);
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        subLevelId = compound.hasUUID("SubLevelID") ? compound.getUUID("SubLevelID") : null;
        platePos = NbtUtils.readBlockPos(compound, "Plate").orElse(null);
        lastException = AssemblyException.read(compound, registries);
        if (clientPacket) {
            rotorSpeed = compound.getFloat("RotorSpeed");
            deliveredWatts = compound.getDouble("Delivered");
        }
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (!isAssembled()) return added;
        CreateLang.builder().add(Component.translatable("create_induction.bearing.title")).forGoggles(tooltip);
        CreateLang.builder()
            .add(Component.translatable("create_induction.bearing.rotor_speed").withStyle(ChatFormatting.GRAY))
            .text(" ")
            .add(Component.literal(Math.round(Math.abs(rotorSpeed)) + " RPM").withStyle(ChatFormatting.AQUA))
            .forGoggles(tooltip, 1);
        CreateLang.builder()
            .add(Component.translatable("create_induction.bearing.delivered").withStyle(ChatFormatting.GRAY))
            .text(" ")
            .add(Component.literal(Math.round(deliveredWatts) + " W").withStyle(ChatFormatting.AQUA))
            .forGoggles(tooltip, 1);
        return true;
    }
}
