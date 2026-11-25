package dev.jensh.createinduction.compat.powergrid;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.jensh.createinduction.Config;
import dev.jensh.createinduction.content.coil.InductionCoilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The coil as a part in a Power Grid circuit: a voltage source in series with the winding resistance, with two
 * wire terminals on its back. Once a wire is attached the circuit decides the current, and that current is what
 * brakes or drives the magnets; without wires the coil falls back to Forge Energy.
 *
 * <p>Generating, the alternating EMF is rectified (as if through a built-in commutator) because the circuit
 * solver works in whole ticks. With a redstone signal the coil is a plain DC winding: current pushed in at the
 * positive terminal makes the face a north pole, and the magnets' back-EMF opposes the supply.
 */
public class PowerGridCoilBlockEntity extends InductionCoilBlockEntity implements IElectricEntity, IElectric {
    private static final Map<Direction, TerminalBoundingBox[]> TERMINALS = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            TERMINALS.put(facing, new TerminalBoundingBox[] {
                terminal(IDecoratedTerminal.POSITIVE, facing, 9, 13).withColor(IDecoratedTerminal.RED),
                terminal(IDecoratedTerminal.NEGATIVE, facing, 3, 7).withColor(IDecoratedTerminal.BLUE)
            });
        }
    }

    private ElectricBehaviour electric;
    private @Nullable VoltageSourceCoupling source;
    /** Current leaving the positive terminal [A], as solved by the circuit. */
    private double circuitCurrent;
    /** Generating, but the circuit sits at a higher voltage than the coil makes: the rectifier is shut. */
    private boolean blocking;
    private static final float BLOCKING_RESISTANCE = 1.0e6f;

    public PowerGridCoilBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** A pad on the back face. {@code from..to} runs across the face in sixteenths; the pad is 4 deep and 2 thick. */
    private static TerminalBoundingBox terminal(net.minecraft.network.chat.Component name, Direction facing, double from, double to) {
        double a = 6, b = 10;
        return switch (facing) {
            case UP -> new TerminalBoundingBox(name, from, 0, a, to, 2, b);
            case DOWN -> new TerminalBoundingBox(name, from, 14, a, to, 16, b);
            case SOUTH -> new TerminalBoundingBox(name, from, a, 0, to, b, 2);
            case NORTH -> new TerminalBoundingBox(name, from, a, 14, to, b, 16);
            case EAST -> new TerminalBoundingBox(name, 0, a, from, 2, b, to);
            case WEST -> new TerminalBoundingBox(name, 14, a, from, 16, b, to);
        };
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        electric = new ElectricBehaviour(this);
        behaviours.add(electric);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2);
        source = builder.addInternalNode(VoltageSourceCoupling.class, builder.terminalNode(0), builder.terminalNode(1),
            Float.valueOf(resistance()));
    }

    @Override
    public float resistance() {
        return Config.COIL_RESISTANCE.get().floatValue();
    }

    @Override
    public float resistance(String suffix) {
        return resistance();
    }

    @Override
    public int terminalCount() {
        return 2;
    }

    @Override
    public ITerminalPlacement terminal(BlockState state, int index) {
        return TERMINALS.get(state.getValue(BlockStateProperties.FACING))[index];
    }

    @Override
    public void remove() {
        super.remove();
        if (electric != null) electric.remove();
    }

    private boolean wired() {
        return electric != null && source != null && !electric.getConnections().isEmpty();
    }

    @Override
    protected void tickFinished(double meanEmf, double rmsEmf) {
        if (source == null) return;
        if (!wired()) {
            blocking = false;
            source.setResistance(resistance());
            source.setVoltage(0);
            circuitCurrent = 0;
            return;
        }
        circuitCurrent = -source.getCurrent();
        if (Double.isNaN(circuitCurrent)) circuitCurrent = 0;
        // Driven: the supply works against the magnets' back-EMF. Generating: the rectified EMF feeds the circuit,
        // and like any rectifier it lets nothing back in, so a battery cannot drain itself through an idle coil.
        if (isDriven()) blocking = false;
        else if (circuitCurrent < 0) blocking = true;
        else if (circuitCurrent > 0) blocking = false;
        source.setResistance(blocking ? BLOCKING_RESISTANCE : resistance());
        source.setVoltage(isDriven() ? -meanEmf : rmsEmf);
    }

    @Override
    protected double desiredCurrent(double emf, double timeStep) {
        if (!wired()) return super.desiredCurrent(emf, timeStep);
        // Coil current is counted positive when it makes the face a north pole, i.e. flowing in at the positive
        // terminal; the circuit reports what flows out of it.
        if (isDriven()) return -circuitCurrent;
        if (circuitCurrent <= 0) return 0;
        // The circuit answers a tick late, so never carry more than this instant's EMF could push through the
        // winding: a generating coil must not end up driving the rotor.
        return Math.copySign(Math.min(circuitCurrent, Math.abs(emf) / resistance()), emf);
    }

    @Override
    protected double settle(double emf, double current, double timeStep) {
        if (!wired()) return super.settle(emf, current, timeStep);
        // Power handed to the circuit; negative while the circuit is driving the coil.
        return emf * current - current * current * resistance();
    }

    @Override
    protected String powerSuffix() {
        return wired() || clientWired ? "" : super.powerSuffix();
    }

    private boolean clientWired;

    @Override
    protected void write(net.minecraft.nbt.CompoundTag compound, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (clientPacket) compound.putBoolean("Wired", wired());
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag compound, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (clientPacket) clientWired = compound.getBoolean("Wired");
    }
}
