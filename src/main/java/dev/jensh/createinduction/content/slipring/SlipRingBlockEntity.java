package dev.jensh.createinduction.content.slipring;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.jensh.createinduction.Config;
import dev.jensh.createinduction.CreateInduction;
import dev.jensh.createinduction.ModContent;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Pairs up with a slip ring facing it from another structure and hands on whatever energy it is given. It stores
 * nothing: energy pushed into one ring comes straight out of the blocks around the other.
 */
public class SlipRingBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final Map<Level, Set<SlipRingBlockEntity>> RINGS = new WeakHashMap<>();

    private final IEnergyStorage energy = new Passage();
    private @Nullable SlipRingBlockEntity partner;
    /** Set while energy is crossing, so nothing can be fed back in a circle. */
    private boolean passing;
    private long movedThisTick;
    private float rate;
    private boolean clientLinked;
    private boolean sentLinked;
    private float sentRate;

    public SlipRingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static SlipRingBlockEntity create(BlockPos pos, BlockState state) {
        BlockEntityType<?> type = ModContent.SLIP_RING_ENTITY.get();
        if (CreateInduction.POWER_GRID) {
            return dev.jensh.createinduction.compat.powergrid.PowerGridCompat.createSlipRing(type, pos, state);
        }
        return new SlipRingBlockEntity(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public IEnergyStorage energy() {
        return energy;
    }

    public @Nullable SlipRingBlockEntity partner() {
        return partner;
    }

    public boolean isLinked() {
        return level != null && level.isClientSide ? clientLinked : partner != null;
    }

    /** Forge Energy per tick passing through, smoothed. */
    public float rate() {
        return rate;
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) RINGS.computeIfAbsent(level, key -> new LinkedHashSet<>()).add(this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level == null || level.isClientSide) return;
        Set<SlipRingBlockEntity> rings = RINGS.get(level);
        if (rings != null) rings.remove(this);
        unlink();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        if (partner != null && (partner.isRemoved() || !touches(partner))) unlink();
        if (partner == null && (level.getGameTime() + worldPosition.asLong()) % 10 == 0) findPartner();
        if (partner != null) whileLinked();

        rate += (movedThisTick - rate) * 0.2f;
        if (rate < 0.05f) rate = 0;
        movedThisTick = 0;
        boolean linked = partner != null;
        if (linked != sentLinked || (level.getGameTime() % 20 == 0 && Math.abs(rate - sentRate) > 0.05f * sentRate + 0.5f)) {
            sentLinked = linked;
            sentRate = rate;
            sendData();
        }
    }

    /** Called every tick while paired, for subclasses that carry more than Forge Energy. */
    protected void whileLinked() {}

    protected void onLinked() {}

    protected void onUnlinked() {}

    // --- contact -------------------------------------------------------------------------------------------

    protected Direction facing() {
        return getBlockState().getValue(BlockStateProperties.FACING);
    }

    /** World position of the middle of the contact face, and the direction it points. */
    protected void contact(Vector3d point, Vector3d normal) {
        Direction facing = facing();
        normal.set(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        point.set(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5).fma(0.5, normal);
        SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel != null) {
            subLevel.logicalPose().transformPosition(point, point);
            subLevel.logicalPose().orientation().transform(normal);
        }
    }

    private boolean touches(SlipRingBlockEntity other) {
        Vector3d point = new Vector3d(), normal = new Vector3d(), otherPoint = new Vector3d(), otherNormal = new Vector3d();
        contact(point, normal);
        other.contact(otherPoint, otherNormal);
        return point.distance(otherPoint) <= Config.SLIP_RING_REACH.get() && normal.dot(otherNormal) < -0.8;
    }

    private void findPartner() {
        Set<SlipRingBlockEntity> rings = RINGS.get(level);
        if (rings == null) return;
        SubLevel own = Sable.HELPER.getContaining(this);
        Vector3d point = new Vector3d(), normal = new Vector3d(), otherPoint = new Vector3d(), otherNormal = new Vector3d();
        contact(point, normal);
        SlipRingBlockEntity best = null;
        double bestDistance = Config.SLIP_RING_REACH.get();
        for (SlipRingBlockEntity other : new ArrayList<>(rings)) {
            if (other == this || other.isRemoved() || other.partner != null) continue;
            if (Sable.HELPER.getContaining(other) == own) continue; // a joint needs two sides
            other.contact(otherPoint, otherNormal);
            double distance = point.distance(otherPoint);
            if (distance > bestDistance || normal.dot(otherNormal) >= -0.8) continue;
            best = other;
            bestDistance = distance;
        }
        if (best == null) return;
        partner = best;
        best.partner = this;
        onLinked();
        best.onLinked();
    }

    private void unlink() {
        SlipRingBlockEntity other = partner;
        if (other == null) return;
        partner = null;
        other.partner = null;
        onUnlinked();
        other.onUnlinked();
    }

    // --- energy --------------------------------------------------------------------------------------------

    /** Offers energy to everything around this ring except through its contact face. */
    protected int distribute(int amount, boolean simulate) {
        int left = amount;
        Direction facing = facing();
        for (Direction side : Direction.values()) {
            if (left <= 0) break;
            if (side == facing) continue;
            BlockPos target = worldPosition.relative(side);
            if (level.getBlockEntity(target) instanceof SlipRingBlockEntity) continue;
            IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, side.getOpposite());
            if (storage == null || !storage.canReceive()) continue;
            left -= storage.receiveEnergy(left, simulate);
        }
        return amount - left;
    }

    private final class Passage implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            SlipRingBlockEntity other = partner;
            if (other == null || other.isRemoved() || passing || other.passing || maxReceive <= 0) return 0;
            passing = true;
            other.passing = true;
            try {
                int accepted = other.distribute(maxReceive, simulate);
                if (!simulate) {
                    movedThisTick += accepted;
                    other.movedThisTick += accepted;
                }
                return accepted;
            } finally {
                passing = false;
                other.passing = false;
            }
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return partner != null;
        }
    }

    // --- sync ----------------------------------------------------------------------------------------------

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (clientPacket) {
            compound.putBoolean("Linked", partner != null);
            compound.putFloat("Rate", rate);
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (clientPacket) {
            clientLinked = compound.getBoolean("Linked");
            rate = compound.getFloat("Rate");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.builder().add(Component.translatable(titleKey())).forGoggles(tooltip);
        CreateLang.builder().add(Component.translatable("create_induction.slip_ring." + (clientLinked ? "linked" : "open")))
            .style(clientLinked ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        if (clientLinked && showsEnergyRate()) {
            CreateLang.builder().add(Component.translatable("create_induction.slip_ring.rate")).style(ChatFormatting.GRAY)
                .space().add(Component.literal(Math.round(rate) + " FE/t").withStyle(ChatFormatting.AQUA)).forGoggles(tooltip, 1);
        }
        return true;
    }

    protected String titleKey() {
        return "create_induction.slip_ring.title";
    }

    protected boolean showsEnergyRate() {
        return true;
    }
}
