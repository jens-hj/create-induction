package dev.jensh.createinduction.compat.powergrid;

import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.Mth;
import dev.jensh.createinduction.content.commutator.CommutatorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.jensh.createinduction.Config;
import dev.jensh.createinduction.CreateInduction;
import dev.jensh.createinduction.content.slipring.SlipRingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The slip ring in a Power Grid circuit: two wire terminals on its sides, each joined to the same terminal of the
 * ring it touches through the small resistance of the brushes.
 */
public class PowerGridSlipRingBlockEntity extends SlipRingBlockEntity implements IElectricEntity, IElectric {
    private static final Map<Direction, TerminalBoundingBox[]> TERMINALS = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            TERMINALS.put(facing, new TerminalBoundingBox[] {
                terminal(facing, true).withColor(IDecoratedTerminal.RED),
                terminal(facing, false).withColor(IDecoratedTerminal.BLUE)
            });
        }
    }

    private ElectricBehaviour electric;
    private final ElectricWire[] brushes = new ElectricWire[2];
    private int retry;
    private static final float OPEN = 1.0e6f, PROBE = 1.0e3f, MINIMUM = 0.25f;
    private @Nullable VoltageSourceCoupling intake;
    private float intakeResistance = OPEN;
    private double pending;

    public PowerGridSlipRingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** A pad on one side of the body, which fills the back half of the block. */
    private static TerminalBoundingBox terminal(Direction facing, boolean first) {
        double[] min = {6, 6, 6}, max = {10, 10, 10};
        int along = facing.getAxis().ordinal();
        int across = facing.getAxis() == Direction.Axis.X ? 2 : 0;
        boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        min[along] = positive ? 2 : 10;
        max[along] = positive ? 6 : 14;
        min[across] = first ? 14 : 0;
        max[across] = first ? 16 : 2;
        return new TerminalBoundingBox(first ? IDecoratedTerminal.POSITIVE : IDecoratedTerminal.NEGATIVE,
            min[0], min[1], min[2], max[0], max[1], max[2]);
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
        // Open unless this ring is the brush box of a commutator; then it is the motor as the circuit sees it.
        intake = builder.addInternalNode(VoltageSourceCoupling.class, builder.terminalNode(0), builder.terminalNode(1),
            Float.valueOf(OPEN));
    }

    /**
     * Brushes on a commutator: draw from the circuit what the armature can use and hand it across. The load is
     * a resistance chosen each tick so that, at the voltage found on the terminals, it takes just that power.
     */
    private void feedCommutator() {
        if (intake == null) return;
        double current = intake.getCurrent();
        if (Double.isNaN(current)) current = 0;
        double volts = Math.abs(current) * intakeResistance;
        double watts = current * current * intakeResistance;
        if (intakeResistance < OPEN) {
            pending += watts * Config.FE_PER_WATT_TICK.get();
            int whole = (int) Math.min(pending, Integer.MAX_VALUE);
            if (whole > 0) pending -= energy().receiveEnergy(whole, false);
            pending = Math.min(pending, 1); // what the armature would not take was lost as heat in the brushes
        }
        double wanted = energy().receiveEnergy(Integer.MAX_VALUE, true) / Config.FE_PER_WATT_TICK.get();
        // Never less resistance than keeps the brush current within what brushes carry.
        float floor = (float) Math.max(MINIMUM, volts / Config.COMMUTATOR_BRUSH_CURRENT.get());
        intakeResistance = wanted <= 0 || volts < 0.01 ? (wanted <= 0 ? OPEN : PROBE)
            : (float) Mth.clamp(volts * volts / wanted, floor, OPEN);
        intake.setResistance(intakeResistance);
        intake.setVoltage(0);
    }

    private void closeIntake() {
        if (intake == null || intakeResistance == OPEN) return;
        intakeResistance = OPEN;
        intake.setResistance(OPEN);
        pending = 0;
    }

    @Override
    public float resistance() {
        return Config.SLIP_RING_RESISTANCE.get().floatValue();
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
        dropBrushes();
        super.remove();
        if (electric != null) electric.remove();
    }

    /** Only one ring of a pair holds the brushes, so they are never doubled. */
    private boolean owns() {
        return partner() != null && worldPosition.asLong() < partner().getBlockPos().asLong();
    }

    @Override
    protected void whileLinked() {
        // Only another wired ring has terminals to join; a commutator is fed instead.
        if (partner() instanceof CommutatorBlockEntity) {
            feedCommutator();
            return;
        }
        if (!(partner() instanceof PowerGridSlipRingBlockEntity)) return;
        if (!owns() || brushes[0] != null || --retry > 0) return;
        retry = 20;
        try {
            for (int i = 0; i < 2; i++) {
                brushes[i] = GlobalElectricNetworks.makeSimpleConnection(level, new BlockWireEndpoint(worldPosition, i),
                    new BlockWireEndpoint(partner().getBlockPos(), i), resistance());
            }
        } catch (RuntimeException e) {
            CreateInduction.LOGGER.debug("Slip ring at {} could not join its partner yet", worldPosition, e);
            dropBrushes();
        }
    }

    @Override
    protected void onUnlinked() {
        closeIntake();
        dropBrushes();
    }

    private void dropBrushes() {
        for (int i = 0; i < 2; i++) {
            if (brushes[i] != null) {
                try {
                    brushes[i].remove();
                } catch (RuntimeException ignored) {
                }
                brushes[i] = null;
            }
        }
    }
}
