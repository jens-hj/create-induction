package dev.jensh.createinduction.physics;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Dipole magnet / coil coupling. Pure maths, no Minecraft types.
 *
 * <p>A magnet is a point dipole with moment {@code m}; a coil is a small loop with unit axis {@code n}.
 * With {@code r = coilPosition - magnetPosition} the flux linked by the coil is
 * {@code flux = (3 (m.r)(n.r) / d^2 - m.n) / d^3}, softened near zero distance.
 *
 * <p>The same expression gives both directions of the machine. A coil carrying current {@code I} with gain
 * {@code k} sees {@code emf = -k dflux/dt}; the magnet feels {@code k I grad(flux)} and the matching torques.
 * Mechanical power on the bodies is then exactly {@code k I dflux/dt = -emf I}: what the load receives is
 * what the rotor loses, and a driven coil does that much work on the magnets.
 */
public final class Induction {
    /** Softening length [blocks]; keeps the field finite when a magnet is right against a coil. */
    public static final double SOFTENING = 0.6;

    private Induction() {}

    private static double softDistanceSquared(Vector3dc r) {
        return r.lengthSquared() + SOFTENING * SOFTENING;
    }

    /** Flux through a coil with axis {@code n} at offset {@code r} from a dipole {@code m}. */
    public static double flux(Vector3dc m, Vector3dc n, Vector3dc r) {
        double d2 = softDistanceSquared(r);
        double d3 = d2 * Math.sqrt(d2);
        return (3 * m.dot(r) * n.dot(r) / d2 - m.dot(n)) / d3;
    }

    /**
     * Coupling vector: {@code flux = other . coupling(axis, r)}. With {@code axis = n} it is the sensitivity of the
     * coil to a moment; with {@code axis = m} it is the magnet's field at the coil.
     */
    public static Vector3d coupling(Vector3dc axis, Vector3dc r, Vector3d dest) {
        double d2 = softDistanceSquared(r);
        double d3 = d2 * Math.sqrt(d2);
        double s = 3 * axis.dot(r) / d2;
        return dest.set(r).mul(s).sub(axis).div(d3);
    }

    /** Gradient of {@link #flux} with respect to {@code r}. */
    public static Vector3d fluxGradient(Vector3dc m, Vector3dc n, Vector3dc r, Vector3d dest) {
        double d2 = softDistanceSquared(r);
        double d5 = d2 * d2 * Math.sqrt(d2);
        double a = m.dot(r);
        double b = n.dot(r);
        double c = m.dot(n);
        double radial = (3 * c - 15 * a * b / d2) / d5;
        return dest.set(
            3 * (m.x() * b + n.x() * a) / d5 + radial * r.x(),
            3 * (m.y() * b + n.y() * a) / d5 + radial * r.y(),
            3 * (m.z() * b + n.z() * a) / d5 + radial * r.z());
    }

    /** One magnet as seen by one coil, all vectors in world space. */
    public static final class Pair {
        /** Offset from magnet to coil. */
        public final Vector3d r = new Vector3d();
        /** Magnet moment and coil axis. */
        public final Vector3d m = new Vector3d();
        public final Vector3d n = new Vector3d();
        /** Velocity of the coil point minus velocity of the magnet point. */
        public final Vector3d relativeVelocity = new Vector3d();
        public final Vector3d magnetSpin = new Vector3d();
        public final Vector3d coilSpin = new Vector3d();

        private final Vector3d gradient = new Vector3d();
        private final Vector3d coilCoupling = new Vector3d();
        private final Vector3d magnetField = new Vector3d();
        private final Vector3d scratch = new Vector3d();

        /** Rate of change of flux for the current motion. Call before the force getters. */
        public double fluxRate() {
            fluxGradient(m, n, r, gradient);
            coupling(n, r, coilCoupling);
            coupling(m, r, magnetField);
            return gradient.dot(relativeVelocity)
                + magnetSpin.cross(m, scratch).dot(coilCoupling)
                + coilSpin.cross(n, scratch).dot(magnetField);
        }

        /** Force on the coil per unit of {@code k I}; the magnet gets the opposite. */
        public Vector3d forceOnCoil(Vector3d dest) {
            return dest.set(gradient);
        }

        /** Torque on the magnet's body per unit of {@code k I}. */
        public Vector3d torqueOnMagnet(Vector3d dest) {
            return m.cross(coilCoupling, dest);
        }

        /** Torque on the coil's body per unit of {@code k I}. */
        public Vector3d torqueOnCoil(Vector3d dest) {
            return n.cross(magnetField, dest);
        }
    }

    /** Current [A] a generating coil pushes through its load. */
    public static double loadCurrent(double emf, double internalResistance, double loadResistance) {
        return emf / (internalResistance + loadResistance);
    }

    /** Electrical power [W] a source must supply to hold {@code current} against {@code emf} (never negative). */
    public static double drivePower(double current, double emf, double internalResistance) {
        return Math.max(0, current * current * internalResistance - emf * current);
    }
}
