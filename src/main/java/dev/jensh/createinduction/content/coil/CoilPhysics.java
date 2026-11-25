package dev.jensh.createinduction.content.coil;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
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
        Step step = new Step(timeStep);
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

        Step(double timeStep) {
            this.timeStep = timeStep;
        }

        /** @return the moving body for a sub-level, or {@code null} for the static world */
        public @Nullable Body body(@Nullable SubLevel subLevel) {
            if (!(subLevel instanceof ServerSubLevel server) || server.isRemoved()) return null;
            return bodies.computeIfAbsent(subLevel, key -> Body.of(server));
        }
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
