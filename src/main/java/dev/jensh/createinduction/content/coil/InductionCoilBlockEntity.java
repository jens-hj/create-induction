package dev.jensh.createinduction.content.coil;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.jensh.createinduction.Config;
import dev.jensh.createinduction.CreateInduction;
import dev.jensh.createinduction.ModContent;
import dev.jensh.createinduction.physics.Induction;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.RedstoneMagnetBlockEntity;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.SimMagnet;
import dev.simulated_team.simulated.util.SimMovementContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public class InductionCoilBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final double TICK = 0.05;

    /** Plot-space positions of magnets close enough to matter, refreshed every tick. */
    private final List<BlockPos> magnets = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private int linkCount;
    private @Nullable SubLevel subLevel;
    private int signal;

    /** Stored energy [FE], kept fractional so small sub-step amounts are not rounded away. */
    private double stored;
    private final IEnergyStorage energy = new CoilEnergy();

    // Totals over the current tick, turned into the displayed values at its end.
    private double emfSquared;
    private double signedEmf;
    private double currentSquared;
    private double electricalEnergy;
    private double seconds;
    private static final double SMOOTHING = 0.1;
    private double smoothedEmfSquared;
    private double smoothedCurrentSquared;
    private double smoothedPower;

    private float voltage;
    private float current;
    /** Positive while generating, negative while consuming [W]. */
    private float power;
    private int syncCooldown;
    private float syncedPower = Float.NaN;

    public InductionCoilBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static InductionCoilBlockEntity create(BlockPos pos, BlockState state) {
        BlockEntityType<?> type = ModContent.INDUCTION_COIL_ENTITY.get();
        if (CreateInduction.POWER_GRID) {
            return dev.jensh.createinduction.compat.powergrid.PowerGridCompat.createCoil(type, pos, state);
        }
        return new InductionCoilBlockEntity(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public IEnergyStorage energy() {
        return energy;
    }

    public float voltage() {
        return voltage;
    }

    public float current() {
        return current;
    }

    public float power() {
        return power;
    }

    public int magnetCount() {
        return magnets.size();
    }

    protected int capacity() {
        return Config.ENERGY_BUFFER.get();
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) CoilPhysics.add(this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null && !level.isClientSide) CoilPhysics.remove(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        subLevel = Sable.HELPER.getContaining(this);
        signal = level.getBestNeighborSignal(worldPosition);
        findMagnets();
        finishTick();
        if (signal == 0) pushEnergy();
    }

    private void findMagnets() {
        magnets.clear();
        SimMovementContext context = SimMovementContext.getMovementContext(level, Vec3.atCenterOf(worldPosition));
        for (SimMovementContext other : RedstoneMagnetBlockEntity.GLOBAL_REDSTONE_MAGNET_MAP.findNearby(context)) {
            if (other.subLevel() != subLevel) magnets.add(other.localBlockPos());
        }
    }

    private void finishTick() {
        // Magnets pass in short pulses, so show a running average rather than one tick's worth.
        double meanEmfSquared = seconds > 0 ? emfSquared / seconds : 0;
        double meanCurrentSquared = seconds > 0 ? currentSquared / seconds : 0;
        double meanPower = seconds > 0 ? electricalEnergy / seconds : 0;
        tickFinished(seconds > 0 ? signedEmf / seconds : 0, Math.sqrt(meanEmfSquared));
        smoothedEmfSquared += (meanEmfSquared - smoothedEmfSquared) * SMOOTHING;
        smoothedCurrentSquared += (meanCurrentSquared - smoothedCurrentSquared) * SMOOTHING;
        smoothedPower += (meanPower - smoothedPower) * SMOOTHING;
        voltage = (float) Math.sqrt(smoothedEmfSquared);
        current = (float) Math.sqrt(smoothedCurrentSquared);
        power = Math.abs(smoothedPower) < 0.01 ? 0 : (float) smoothedPower;
        emfSquared = 0;
        signedEmf = 0;
        currentSquared = 0;
        electricalEnergy = 0;
        seconds = 0;

        if (--syncCooldown <= 0 && (Float.isNaN(syncedPower) || Math.abs(power - syncedPower) > 0.5f + 0.02f * Math.abs(syncedPower))) {
            syncedPower = power;
            syncCooldown = 10;
            setChanged();
            sendData();
        }
    }

    private void pushEnergy() {
        if (stored < 1) return;
        for (Direction side : Direction.values()) {
            BlockPos target = worldPosition.relative(side);
            if (level.getBlockEntity(target) instanceof InductionCoilBlockEntity) continue;
            IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, side.getOpposite());
            if (storage == null || !storage.canReceive()) continue;
            int sent = storage.receiveEnergy((int) Math.min(stored, Integer.MAX_VALUE), false);
            if (sent > 0) {
                stored -= sent;
                setChanged();
            }
            if (stored < 1) return;
        }
    }

    protected int signal() {
        return signal;
    }

    // --- physics ---------------------------------------------------------------------------------------------

    /** The current [A] this coil wants to carry this sub-step. Positive makes the facing side a north pole. */
    protected double desiredCurrent(double emf, double timeStep) {
        if (signal > 0) {
            double drive = signal / 15.0 * Config.DRIVE_CURRENT.get();
            double cost = Induction.drivePower(drive, emf, Config.COIL_RESISTANCE.get()) * fePerWattSecond() * timeStep;
            return stored >= cost ? drive : 0;
        }
        if (stored >= capacity()) return 0;
        return Induction.loadCurrent(emf, Config.COIL_RESISTANCE.get(), Config.LOAD_RESISTANCE.get());
    }

    /**
     * Books the electrical side of a sub-step once the current is final.
     *
     * @return electrical power [W]; positive when delivered to the load, negative when drawn from the supply
     */
    protected double settle(double emf, double current, double timeStep) {
        if (signal > 0) {
            double drawn = Induction.drivePower(current, emf, Config.COIL_RESISTANCE.get());
            stored = Math.max(0, stored - drawn * fePerWattSecond() * timeStep);
            return -drawn;
        }
        double delivered = current * current * Config.LOAD_RESISTANCE.get();
        stored = Math.min(capacity(), stored + delivered * fePerWattSecond() * timeStep);
        return delivered;
    }

    /** Called once per game tick with the EMF of the tick that just ended: its mean and its RMS [V]. */
    protected void tickFinished(double meanEmf, double rmsEmf) {}

    protected static double fePerWattSecond() {
        return Config.FE_PER_WATT_TICK.get() / TICK;
    }

    protected boolean isDriven() {
        return signal > 0;
    }

    void physicsStep(CoilPhysics.Step step) {
        if (level == null) return;
        double timeStep = step.timeStep;
        seconds += timeStep;
        if (magnets.isEmpty()) {
            settleIdle(timeStep);
            return;
        }

        CoilPhysics.Body coilBody = step.body(subLevel);
        if (subLevel != null && coilBody == null) return;

        Vector3d coilLocal = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        Vector3d coilWorld = new Vector3d(coilLocal);
        Vec3i normal = getBlockState().getValue(BlockStateProperties.FACING).getNormal();
        Vector3d axis = new Vector3d(normal.getX(), normal.getY(), normal.getZ());
        Vector3d coilVelocity = new Vector3d();
        if (coilBody != null) {
            coilBody.pose.transformPosition(coilLocal, coilWorld);
            coilBody.orientation.transform(axis);
            coilBody.velocityAt(coilWorld, coilVelocity);
        }

        double range = Config.RANGE.get();
        double fluxRate = 0;
        linkCount = 0;
        Vector3d magnetVelocity = new Vector3d();
        for (BlockPos magnetPos : magnets) {
            BlockEntity blockEntity = level.getBlockEntity(magnetPos);
            if (blockEntity == null || blockEntity.isRemoved() || !(blockEntity instanceof SimMagnet magnet) || !magnet.magnetActive()) continue;
            SubLevel magnetSubLevel = magnet.getLatestSubLevel();
            if (magnetSubLevel == subLevel) continue;
            CoilPhysics.Body magnetBody = step.body(magnetSubLevel);
            if (magnetSubLevel != null && magnetBody == null) continue;

            Link link = nextLink();
            link.body = magnetBody;
            Vec3 magnetLocal = magnet.getMagnetPosition();
            link.local.set(magnetLocal.x, magnetLocal.y, magnetLocal.z);
            link.world.set(link.local);
            magnet.setMagneticMoment(link.pair.m);
            magnetVelocity.zero();
            link.pair.magnetSpin.zero();
            if (magnetBody != null) {
                magnetBody.pose.transformPosition(link.local, link.world);
                magnetBody.orientation.transform(link.pair.m);
                magnetBody.velocityAt(link.world, magnetVelocity);
                link.pair.magnetSpin.set(magnetBody.angularVelocity);
            }
            coilWorld.sub(link.world, link.pair.r);
            if (link.pair.r.lengthSquared() > range * range) {
                linkCount--;
                continue;
            }
            link.pair.n.set(axis);
            coilVelocity.sub(magnetVelocity, link.pair.relativeVelocity);
            if (coilBody != null) link.pair.coilSpin.set(coilBody.angularVelocity);
            else link.pair.coilSpin.zero();
            fluxRate += link.pair.fluxRate();
        }

        double gain = Config.COIL_GAIN.get();
        double emf = -gain * fluxRate;
        double current = linkCount == 0 ? idleCurrent(emf, timeStep) : desiredCurrent(emf, timeStep);
        if (current != 0 && linkCount > 0) {
            double strength = gain * current / Config.WATTS_PER_PHYSICS_POWER.get();
            double limit = accelerationLimit(step, strength, coilBody, coilWorld);
            current *= limit;
            applyForces(strength * limit, coilBody, coilLocal, timeStep);
        }
        record(emf, current, settle(emf, current, timeStep), timeStep);
    }

    private double idleCurrent(double emf, double timeStep) {
        // Nothing to push against: a driven coil still burns its supply, a generating one carries nothing.
        return isDriven() ? desiredCurrent(emf, timeStep) : 0;
    }

    private void settleIdle(double timeStep) {
        double current = idleCurrent(0, timeStep);
        record(0, current, settle(0, current, timeStep), timeStep);
    }

    private void record(double emf, double current, double electricalPower, double timeStep) {
        emfSquared += emf * emf * timeStep;
        signedEmf += emf * timeStep;
        currentSquared += current * current * timeStep;
        electricalEnergy += electricalPower * timeStep;
    }

    private Link nextLink() {
        if (linkCount == links.size()) links.add(new Link());
        return links.get(linkCount++);
    }

    /** Scale (0..1] that keeps every body under the configured acceleration for this sub-step. */
    /** Scales the current down where it would throw a body about harder than the configured acceleration. */
    private double accelerationLimit(CoilPhysics.Step step, double strength, @Nullable CoilPhysics.Body coilBody, Vector3d coilWorld) {
        double maxAcceleration = Config.MAX_ACCELERATION.get();
        double limit = 1;
        Vector3d force = new Vector3d();
        for (int i = 0; i < linkCount; i++) {
            Link link = links.get(i);
            link.pair.forceOnCoil(force).mul(strength);
            double magnitude = force.length();
            if (magnitude < 1.0e-9) continue;
            if (coilBody != null) {
                double acceleration = magnitude * step.inverseMass(coilBody, coilWorld, force);
                if (acceleration > maxAcceleration) limit = Math.min(limit, maxAcceleration / acceleration);
            }
            if (link.body != null) {
                double acceleration = magnitude * step.inverseMass(link.body, link.world, force);
                if (acceleration > maxAcceleration) limit = Math.min(limit, maxAcceleration / acceleration);
            }
        }
        return limit;
    }

    private void applyForces(double strength, @Nullable CoilPhysics.Body coilBody, Vector3d coilLocal, double timeStep) {
        Vector3d force = new Vector3d();
        Vector3d torque = new Vector3d();
        Vector3d coilForce = new Vector3d();
        Vector3d coilTorque = new Vector3d();
        for (int i = 0; i < linkCount; i++) {
            Link link = links.get(i);
            link.pair.forceOnCoil(force).mul(strength);
            coilForce.add(force);
            coilTorque.add(link.pair.torqueOnCoil(torque).mul(strength));
            if (link.body != null) {
                link.pair.torqueOnMagnet(torque).mul(strength);
                push(link.body, link.local, force.negate(), torque, timeStep);
            }
        }
        if (coilBody != null) push(coilBody, coilLocal, coilForce, coilTorque, timeStep);
    }

    /** Applies a world-space force at a plot-space point, plus a world-space torque, as impulses. */
    private static void push(CoilPhysics.Body body, Vector3d plotPoint, Vector3d force, Vector3d torque, double timeStep) {
        ServerSubLevel target = body.subLevel;
        QueuedForceGroup group = target.getOrCreateQueuedForceGroup(ForceGroups.MAGNETIC_FORCE.get());
        Vector3d localForce = body.orientation.transformInverse(force, new Vector3d()).mul(timeStep);
        Vector3d localTorque = body.orientation.transformInverse(torque, new Vector3d()).mul(timeStep);
        group.applyAndRecordPointForce(plotPoint, localForce);
        group.getForceTotal().applyLinearAndAngularImpulse(new Vector3d(), localTorque);
    }

    private static final class Link {
        final Induction.Pair pair = new Induction.Pair();
        final Vector3d local = new Vector3d();
        final Vector3d world = new Vector3d();
        @Nullable CoilPhysics.Body body;
    }

    // --- data ------------------------------------------------------------------------------------------------

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putDouble("Energy", stored);
        if (clientPacket) {
            compound.putFloat("Voltage", voltage);
            compound.putFloat("Current", current);
            compound.putFloat("Power", power);
            compound.putInt("Magnets", magnets.size());
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        stored = compound.getDouble("Energy");
        if (clientPacket) {
            voltage = compound.getFloat("Voltage");
            current = compound.getFloat("Current");
            power = compound.getFloat("Power");
            clientMagnets = compound.getInt("Magnets");
        }
    }

    private int clientMagnets;

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.builder().add(Component.translatable("create_induction.coil.title")).forGoggles(tooltip);
        String state = power > 0.5f ? "generating" : power < -0.5f ? "driving" : "idle";
        CreateLang.builder().add(Component.translatable("create_induction.coil." + state))
            .style(power > 0.5f ? ChatFormatting.GREEN : power < -0.5f ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)
            .forGoggles(tooltip, 1);
        line(tooltip, "create_induction.coil.power", format(Math.abs(power)) + " W" + powerSuffix());
        line(tooltip, "create_induction.coil.voltage", format(voltage) + " V");
        line(tooltip, "create_induction.coil.current", format(current) + " A");
        line(tooltip, "create_induction.coil.stored", Mth.floor(stored) + " / " + capacity() + " FE");
        line(tooltip, "create_induction.coil.magnets", Integer.toString(clientMagnets));
        return true;
    }

    /** Extra text after the wattage on the goggles. */
    protected String powerSuffix() {
        return " (" + format((float) (Math.abs(power) * Config.FE_PER_WATT_TICK.get())) + " FE/t)";
    }

    private static void line(List<Component> tooltip, String key, String value) {
        CreateLang.builder()
            .add(Component.translatable(key).withStyle(ChatFormatting.GRAY))
            .text(" ")
            .add(Component.literal(value).withStyle(ChatFormatting.AQUA))
            .forGoggles(tooltip, 1);
    }

    private static String format(float value) {
        return value >= 100 ? Integer.toString(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private final class CoilEnergy implements IEnergyStorage {
        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            int accepted = (int) Math.max(0, Math.min(amount, Math.floor(capacity() - stored)));
            if (!simulate && accepted > 0) {
                stored += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            int taken = (int) Math.max(0, Math.min(amount, Math.floor(stored)));
            if (!simulate && taken > 0) {
                stored -= taken;
                setChanged();
            }
            return taken;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.floor(stored);
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
