package dev.jensh.createinduction.content.bearing;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.jensh.createinduction.ModContent;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class DynamoPlateBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {
    private @Nullable BlockPos parent;
    private @Nullable UUID parentSubLevelId;
    /** Set when the block is removed on purpose (disassembly, being moved), so the bearing is left alone. */
    private boolean silenced;

    public DynamoPlateBlockEntity(BlockPos pos, BlockState state) {
        this(ModContent.DYNAMO_BEARING_PLATE_ENTITY.get(), pos, state);
    }

    public DynamoPlateBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    void setParent(DynamoBearingBlockEntity bearing) {
        SubLevel containing = Sable.HELPER.getContaining(bearing);
        parent = bearing.getBlockPos();
        parentSubLevelId = containing == null ? null : containing.getUniqueId();
        setChanged();
    }

    void silence() {
        silenced = true;
    }

    private @Nullable DynamoBearingBlockEntity bearing() {
        if (level == null || parent == null) return null;
        return level.getBlockEntity(parent) instanceof DynamoBearingBlockEntity bearing ? bearing : null;
    }

    void toggleParent() {
        DynamoBearingBlockEntity bearing = bearing();
        if (bearing != null) bearing.assembleNextTick = true;
    }

    /** After Sable moved this block (split, merge, re-assembly): tell the bearing where its rotor went. */
    void relink() {
        DynamoBearingBlockEntity bearing = bearing();
        if (level == null || level.isClientSide || bearing == null) return;
        bearing.plateMoved(getBlockPos(), Sable.HELPER.getContaining(this) instanceof ServerSubLevel rotor ? rotor : null);
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide && !silenced) {
            DynamoBearingBlockEntity bearing = bearing();
            if (bearing != null && getBlockPos().equals(bearing.platePos())) bearing.onPlateLost();
        }
        super.remove();
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        DynamoBearingBlockEntity bearing = bearing();
        if (bearing != null) bearing.drive(subLevel, handle, timeStep);
    }

    @Override
    public @Nullable Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        if (level == null || parent == null || parentSubLevelId == null) return null;
        SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(parentSubLevelId);
        return subLevel == null ? null : List.of(subLevel);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (parent != null) compound.put("Parent", NbtUtils.writeBlockPos(parent));
        if (parentSubLevelId != null) compound.putUUID("ParentSubLevelID", parentSubLevelId);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        parent = NbtUtils.readBlockPos(compound, "Parent").orElse(null);
        parentSubLevelId = compound.hasUUID("ParentSubLevelID") ? compound.getUUID("ParentSubLevelID") : null;
    }
}
