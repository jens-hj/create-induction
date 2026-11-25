package dev.jensh.createinduction;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue MAGNET_STRENGTH;
    public static final ModConfigSpec.DoubleValue RANGE;
    public static final ModConfigSpec.DoubleValue MAX_ACCELERATION;
    public static final ModConfigSpec.DoubleValue WATTS_PER_PHYSICS_POWER;

    public static final ModConfigSpec.DoubleValue COIL_GAIN;
    public static final ModConfigSpec.DoubleValue COIL_RESISTANCE;
    public static final ModConfigSpec.DoubleValue LOAD_RESISTANCE;
    public static final ModConfigSpec.DoubleValue DRIVE_CURRENT;
    public static final ModConfigSpec.DoubleValue FE_PER_WATT_TICK;
    public static final ModConfigSpec.IntValue ENERGY_BUFFER;

    public static final ModConfigSpec.DoubleValue SLIP_RING_REACH;
    public static final ModConfigSpec.DoubleValue SLIP_RING_RESISTANCE;
    public static final ModConfigSpec.DoubleValue COMMUTATOR_VOLTAGE;

    public static final ModConfigSpec.DoubleValue BEARING_GAIN;
    public static final ModConfigSpec.DoubleValue BEARING_MAX_TORQUE;
    public static final ModConfigSpec.DoubleValue BEARING_MAX_POWER;
    public static final ModConfigSpec.DoubleValue BEARING_FRICTION;
    public static final ModConfigSpec.DoubleValue BEARING_BASE_IMPACT;
    public static final ModConfigSpec.DoubleValue BEARING_OUTPUT_CAPACITY;
    public static final ModConfigSpec.DoubleValue BEARING_OUTPUT_START;
    public static final ModConfigSpec.DoubleValue SU_PER_WATT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("magnetism");
        MAGNET_STRENGTH = builder
            .comment("Strength of a Permanent Magnet. A Redstone Magnet at full signal is 1000 by default.")
            .defineInRange("permanentMagnetStrength", 1000.0, 0.0, 1.0e6);
        RANGE = builder
            .comment("How far [blocks] an Induction Coil feels magnets.")
            .defineInRange("coilRange", 8.0, 1.0, 16.0);
        MAX_ACCELERATION = builder
            .comment("Cap on the acceleration [m/s^2] a single coil may give a structure. Keeps tight builds stable.")
            .defineInRange("maxAcceleration", 60.0, 1.0, 1000.0);
        WATTS_PER_PHYSICS_POWER = builder
            .comment("Watts per unit of physics power (force x speed). Raise for more electricity from the same braking.")
            .defineInRange("wattsPerPhysicsPower", 1.0, 0.001, 1000.0);
        builder.pop();

        builder.push("coil");
        COIL_GAIN = builder
            .comment("Volts per unit of flux change per second; also force per amp. The main coil strength knob.")
            .defineInRange("gain", 0.5, 0.0, 100.0);
        COIL_RESISTANCE = builder
            .comment("Internal resistance [ohm]. Lost as heat.")
            .defineInRange("resistance", 0.5, 0.01, 1000.0);
        LOAD_RESISTANCE = builder
            .comment("Resistance [ohm] of the Forge Energy load a generating coil works into.")
            .defineInRange("loadResistance", 2.0, 0.01, 1000.0);
        DRIVE_CURRENT = builder
            .comment("Current [A] through a coil powered with a full redstone signal.")
            .defineInRange("driveCurrent", 20.0, 0.0, 1000.0);
        FE_PER_WATT_TICK = builder
            .comment("Forge Energy per tick for one watt. The default makes a coil about as good as a Crafts & Additions",
                "alternator for the stress it costs. Power Grid's own exchange rate is 10.")
            .defineInRange("fePerWattTick", 0.2, 0.0, 10000.0);
        ENERGY_BUFFER = builder
            .comment("Forge Energy buffer of a coil.")
            .defineInRange("energyBuffer", 4000, 100, Integer.MAX_VALUE);
        builder.pop();

        builder.push("dynamoBearing");
        BEARING_GAIN = builder
            .comment("How hard the bearing chases its shaft speed [1/s].")
            .defineInRange("gain", 6.0, 0.1, 100.0);
        BEARING_MAX_TORQUE = builder
            .comment("Torque limit of the bearing [physics units].")
            .defineInRange("maxTorque", 20000.0, 1.0, 1.0e7);
        BEARING_MAX_POWER = builder
            .comment("Mechanical power limit of the bearing [W].")
            .defineInRange("maxPower", 64000.0, 1.0, 1.0e7);
        BEARING_FRICTION = builder
            .comment("Passive friction of the bearing joint.")
            .defineInRange("friction", 0.0, 0.0, 100.0);
        BEARING_BASE_IMPACT = builder
            .comment("Stress impact of an idle bearing [SU per RPM].")
            .defineInRange("baseImpact", 2.0, 0.0, 1024.0);
        BEARING_OUTPUT_CAPACITY = builder
            .comment("Stress capacity a rotor-driven bearing offers its shaft [SU per RPM]. What the rotor can really",
                "carry is decided by the physics: too much load and it slows down and stalls.")
            .defineInRange("outputCapacity", 512.0, 0.0, 65536.0);
        BEARING_OUTPUT_START = builder
            .comment("Rotor speed [RPM] from which an undriven bearing starts turning its shaft. It lets go at half this.")
            .defineInRange("outputStart", 8.0, 1.0, 256.0);
        SU_PER_WATT = builder
            .comment("Stress units charged per watt of mechanical power delivered. Power Grid is about 6.25.")
            .defineInRange("suPerWatt", 6.25, 0.0, 10000.0);
        builder.pop();

        builder.push("slipRing");
        SLIP_RING_REACH = builder
            .comment("How far apart [blocks] the contact faces of two Slip Rings may be and still touch.")
            .defineInRange("reach", 0.75, 0.1, 4.0);
        SLIP_RING_RESISTANCE = builder
            .comment("Resistance [ohm] of each brush contact in a Power Grid circuit.")
            .defineInRange("resistance", 0.05, 0.0001, 1000.0);
        COMMUTATOR_VOLTAGE = builder
            .comment("Voltage [V] a Commutator puts across each coil of its armature. Higher means a faster motor;",
                "the current is still limited to the coil's drive current.")
            .defineInRange("commutatorVoltage", 12.0, 0.1, 10000.0);
        builder.pop();

        SPEC = builder.build();
    }

    private Config() {}
}
