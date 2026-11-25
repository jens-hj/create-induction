package dev.jensh.createinduction.content.coil;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Steps every loaded coil once per physics sub-step, which is where forces have to be applied. */
public final class CoilPhysics {
    private static final Map<Level, Set<InductionCoilBlockEntity>> COILS = new WeakHashMap<>();

    private CoilPhysics() {}

    static void add(InductionCoilBlockEntity coil) {
        COILS.computeIfAbsent(coil.getLevel(), level -> new LinkedHashSet<>()).add(coil);
    }

    static void remove(InductionCoilBlockEntity coil) {
        Set<InductionCoilBlockEntity> coils = COILS.get(coil.getLevel());
        if (coils != null) coils.remove(coil);
    }

    public static void physicsTick(SubLevelPhysicsSystem system, double timeStep) {
        Set<InductionCoilBlockEntity> coils = COILS.get(system.getLevel());
        if (coils == null || coils.isEmpty()) return;
        Step step = new Step(system.getLevel(), timeStep);
        List<InductionCoilBlockEntity> snapshot = new ArrayList<>(coils);
        for (InductionCoilBlockEntity coil : snapshot) {
            if (coil.isRemoved()) {
                coils.remove(coil);
                continue;
            }
            coil.physicsStep(step);
        }
    }

    /** One physics sub-step; caches the motion of each body touched. */
    public static final class Step {
        public final double timeStep;
        private final Map<SubLevel, Body> bodies = new HashMap<>();

        private final Map<Body, Carrier> carriers = new HashMap<>();
        private final Level level;
        private @Nullable List<Body> everything;

        Step(Level level, double timeStep) {
            this.level = level;
            this.timeStep = timeStep;
        }

        /**
         * Inverse effective mass felt when pushing {@code body} at a world point along a world direction. A body
         * that moves as one with something else (riveted, welded, resting on the ground) is as hard to push as
         * whatever carries it, however little it weighs itself.
         */
        public double inverseMass(Body body, Vector3dc worldPoint, Vector3dc worldDirection) {
            Carrier carrier = carriers.computeIfAbsent(body, this::findCarrier);
            if (carrier.world) return 0;
            Body target = carrier.body != null ? carrier.body : body;
            Vector3d point = target.pose.transformPositionInverse(worldPoint, new Vector3d());
            Vector3d direction = target.orientation.transformInverse(worldDirection, new Vector3d());
            return target.subLevel.getMassTracker().getInverseNormalMass(point, direction);
        }

        private Carrier findCarrier(Body body) {
            if (body.linearVelocity.length() < 0.02 && body.angularVelocity.length() < 0.02) return Carrier.WORLD;
            if (everything == null) {
                everything = new ArrayList<>();
                ServerSubLevelContainer container = SubLevelContainer.getContainer((net.minecraft.server.level.ServerLevel) level);
                if (container != null) {
                    for (ServerSubLevel other : container.getAllSubLevels()) {
                        Body otherBody = body(other);
                        if (otherBody != null) everything.add(otherBody);
                    }
                }
            }
            double inverseMass = body.subLevel.getMassTracker().getInverseMass();
            Vector3d there = new Vector3d();
            Body best = null;
            for (Body other : everything) {
                if (other == body) continue;
                double otherInverseMass = other.subLevel.getMassTracker().getInverseMass();
                if (otherInverseMass >= inverseMass) continue; // only something heavier can carry this
                if (best != null && otherInverseMass >= best.subLevel.getMassTracker().getInverseMass()) continue;
                if (other.angularVelocity.distance(body.angularVelocity) > 0.03 + 0.03 * body.angularVelocity.length()) continue;
                other.velocityAt(body.centerOfMass, there);
                if (there.distance(body.linearVelocity) > 0.08 + 0.03 * body.linearVelocity.length()) continue;
                best = other;
            }
            return best == null ? Carrier.NONE : new Carrier(false, best);
        }

        /** @return the moving body for a sub-level, or {@code null} for the static world */
        public @Nullable Body body(@Nullable SubLevel subLevel) {
            if (!(subLevel instanceof ServerSubLevel server) || server.isRemoved()) return null;
            return bodies.computeIfAbsent(subLevel, key -> Body.of(server));
        }
    }

    private record Carrier(boolean world, @Nullable Body body) {
        static final Carrier WORLD = new Carrier(true, null);
        static final Carrier NONE = new Carrier(false, null);
    }

    /** Snapshot of a sub-level's rigid body in world space. */
    public static final class Body {
        public final ServerSubLevel subLevel;
        public final Pose3dc pose;
        public final Quaterniond orientation = new Quaterniond();
        public final Vector3d linearVelocity = new Vector3d();
        public final Vector3d angularVelocity = new Vector3d();
        public final Vector3d centerOfMass = new Vector3d();

        private Body(ServerSubLevel subLevel) {
            this.subLevel = subLevel;
            this.pose = subLevel.logicalPose();
            this.orientation.set(pose.orientation());
        }

        static @Nullable Body of(ServerSubLevel subLevel) {
            RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            Vector3dc plotCenterOfMass = subLevel.getMassTracker().getCenterOfMass();
            if (handle == null || !handle.isValid() || plotCenterOfMass == null) return null;
            Body body = new Body(subLevel);
            handle.getLinearVelocity(body.linearVelocity);
            handle.getAngularVelocity(body.angularVelocity);
            body.pose.transformPosition(plotCenterOfMass, body.centerOfMass);
            return body;
        }

        /** Velocity of a world-space point riding on this body. */
        public Vector3d velocityAt(Vector3dc worldPoint, Vector3d dest) {
            worldPoint.sub(centerOfMass, dest);
            return angularVelocity.cross(dest, dest).add(linearVelocity);
        }
    }
}
