package dev.jensh.createinduction.physics;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InductionTest {
    private static Vector3d random(Random random, double scale) {
        return new Vector3d(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).mul(2 * scale);
    }

    @Test
    void gradientMatchesFiniteDifference() {
        Random random = new Random(1);
        for (int i = 0; i < 200; i++) {
            Vector3d m = random(random, 30), n = random(random, 1).normalize(), r = random(random, 4);
            Vector3d gradient = Induction.fluxGradient(m, n, r, new Vector3d());
            double h = 1e-5;
            for (int axis = 0; axis < 3; axis++) {
                Vector3d plus = new Vector3d(r), minus = new Vector3d(r);
                plus.setComponent(axis, r.get(axis) + h);
                minus.setComponent(axis, r.get(axis) - h);
                double numeric = (Induction.flux(m, n, plus) - Induction.flux(m, n, minus)) / (2 * h);
                assertEquals(numeric, gradient.get(axis), 1e-5 * Math.max(1, Math.abs(numeric)));
            }
        }
    }

    @Test
    void couplingReproducesFlux() {
        Random random = new Random(2);
        for (int i = 0; i < 200; i++) {
            Vector3d m = random(random, 30), n = random(random, 1).normalize(), r = random(random, 4);
            double flux = Induction.flux(m, n, r);
            assertEquals(flux, m.dot(Induction.coupling(n, r, new Vector3d())), 1e-9 * Math.max(1, Math.abs(flux)));
            assertEquals(flux, n.dot(Induction.coupling(m, r, new Vector3d())), 1e-9 * Math.max(1, Math.abs(flux)));
        }
    }

    /** Two free rigid bodies: the analytic flux rate must match the flux actually changing as they move. */
    @Test
    void fluxRateMatchesMotion() {
        Random random = new Random(3);
        for (int i = 0; i < 200; i++) {
            Vector3d magnetPosition = random(random, 3), coilPosition = random(random, 3);
            Vector3d m = random(random, 30), n = random(random, 1).normalize();
            Vector3d magnetVelocity = random(random, 10), coilVelocity = random(random, 10);
            Vector3d magnetSpin = random(random, 5), coilSpin = random(random, 5);

            Induction.Pair pair = new Induction.Pair();
            pair.m.set(m);
            pair.n.set(n);
            coilPosition.sub(magnetPosition, pair.r);
            coilVelocity.sub(magnetVelocity, pair.relativeVelocity);
            pair.magnetSpin.set(magnetSpin);
            pair.coilSpin.set(coilSpin);
            double rate = pair.fluxRate();

            double dt = 1e-6;
            double before = fluxAt(-dt, magnetPosition, coilPosition, m, n, magnetVelocity, coilVelocity, magnetSpin, coilSpin);
            double after = fluxAt(dt, magnetPosition, coilPosition, m, n, magnetVelocity, coilVelocity, magnetSpin, coilSpin);
            double numeric = (after - before) / (2 * dt);
            assertEquals(numeric, rate, 1e-4 * Math.max(1, Math.abs(numeric)));
        }
    }

    private static double fluxAt(double t, Vector3d magnetPosition, Vector3d coilPosition, Vector3d m, Vector3d n,
                                 Vector3d magnetVelocity, Vector3d coilVelocity, Vector3d magnetSpin, Vector3d coilSpin) {
        Vector3d r = new Vector3d(coilPosition).fma(t, coilVelocity).sub(new Vector3d(magnetPosition).fma(t, magnetVelocity));
        Vector3d movedM = rotate(magnetSpin, t).transform(new Vector3d(m));
        Vector3d movedN = rotate(coilSpin, t).transform(new Vector3d(n));
        return Induction.flux(movedM, movedN, r);
    }

    private static Quaterniond rotate(Vector3d spin, double t) {
        double angle = spin.length() * t;
        Vector3d axis = new Vector3d(spin).normalize();
        return new Quaterniond().rotateAxis(angle, axis);
    }

    /** Mechanical power on both bodies equals {@code k I dflux/dt}, so nothing is created or lost. */
    @Test
    void mechanicalPowerBalancesElectricalPower() {
        Random random = new Random(4);
        for (int i = 0; i < 200; i++) {
            Induction.Pair pair = new Induction.Pair();
            pair.m.set(random(random, 30));
            pair.n.set(random(random, 1).normalize());
            pair.r.set(random(random, 4));
            Vector3d magnetVelocity = random(random, 10), coilVelocity = random(random, 10);
            coilVelocity.sub(magnetVelocity, pair.relativeVelocity);
            pair.magnetSpin.set(random(random, 5));
            pair.coilSpin.set(random(random, 5));
            double rate = pair.fluxRate();

            Vector3d force = pair.forceOnCoil(new Vector3d());
            double power = force.dot(coilVelocity) - force.dot(magnetVelocity)
                + pair.torqueOnMagnet(new Vector3d()).dot(pair.magnetSpin)
                + pair.torqueOnCoil(new Vector3d()).dot(pair.coilSpin);
            assertEquals(rate, power, 1e-9 * Math.max(1, Math.abs(rate)));
        }
    }

    /** A magnet and a coil bolted to the same spinning body see no flux change. */
    @Test
    void comovingMagnetInducesNothing() {
        Random random = new Random(5);
        for (int i = 0; i < 200; i++) {
            Vector3d spin = random(random, 5);
            Induction.Pair pair = new Induction.Pair();
            pair.m.set(random(random, 30));
            pair.n.set(random(random, 1).normalize());
            pair.r.set(random(random, 4));
            spin.cross(pair.r, pair.relativeVelocity);
            pair.magnetSpin.set(spin);
            pair.coilSpin.set(spin);
            assertEquals(0, pair.fluxRate(), 1e-9 * (1 + pair.m.length()));
        }
    }

    /** A generating coil always brakes: the current follows the EMF, so mechanical power is never positive. */
    @Test
    void generatingAlwaysBrakes() {
        Random random = new Random(6);
        for (int i = 0; i < 200; i++) {
            double rate = (random.nextDouble() - 0.5) * 100;
            double gain = 0.2;
            double emf = -gain * rate;
            double current = Induction.loadCurrent(emf, 2, 8);
            assertTrue(gain * current * rate <= 0);
        }
        assertEquals(0, Induction.drivePower(0, 5, 2));
        assertEquals(200, Induction.drivePower(10, 0, 2));
    }
}
