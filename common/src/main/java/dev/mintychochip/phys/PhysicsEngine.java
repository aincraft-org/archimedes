package dev.mintychochip.phys;

import java.util.Collection;
import java.util.Objects;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Stateless semi-implicit Euler integrator for active rigid bodies.
 *
 * <p>Each step sums the body's explicit forces and torques, updates linear and angular velocity,
 * then advances position and orientation by the world's timestep. Gravity is not implicit; callers
 * must attach a gravity force when they want it.
 */
public final class PhysicsEngine implements Physics {
  /**
   * Advances each active body by one world timestep.
   *
   * @param world world supplying the timestep and force inputs
   * @param bodies bodies to integrate; inactive bodies are skipped
   * @throws NullPointerException if either argument is {@code null}
   * @throws IllegalArgumentException if the world timestep is negative or non-finite
   */
  public void step(World world, Collection<Body> bodies) {
    Objects.requireNonNull(world);
    Objects.requireNonNull(bodies);
    double dt = world.timeStep();
    if (!Double.isFinite(dt) || dt < 0) throw new IllegalArgumentException("bad timestep");
    for (Body body : bodies) {
      if (!body.active()) continue;
      Vector3d totalForce = new Vector3d();
      Vector3d totalTorque = new Vector3d();
      for (Force force : body.forces()) {
        Force.Result r = force.apply(body, world);
        totalForce.add(r.force());
        totalTorque.add(r.torque());
      }
      Vector3d acc = totalForce.mul(body.inverseMass(), new Vector3d());
      Vector3d v = new Vector3d(body.linearVelocity());
      Vector3d newV = v.add(acc.mul(dt, new Vector3d()), new Vector3d());
      body.setLinearVelocity(newV);

      Vector3d angularAcc = body.inverseInertia().transform(totalTorque, new Vector3d());
      Vector3d omega = new Vector3d(body.angularVelocity());
      Vector3d newOmega = omega.add(angularAcc.mul(dt, new Vector3d()), new Vector3d());
      body.setAngularVelocity(newOmega);

      Vector3d p = new Vector3d(body.transform().position());
      Quaterniond q = new Quaterniond(body.transform().orientation());
      Quaterniond newQ = q.integrate(dt, newOmega.x(), newOmega.y(), newOmega.z());
      body.setTransform(new Transform(p.add(newV.mul(dt, new Vector3d()), new Vector3d()), newQ));
    }
    Collisions.resolve(Collisions.detect(bodies));
  }
}
