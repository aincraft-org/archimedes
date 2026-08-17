package dev.mintychochip.phys;

import java.util.Collection;
import java.util.Objects;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class PhysicsEngine implements Physics {
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
      Quaterniond newQ = q.integrate(newOmega.x(), newOmega.y(), newOmega.z(), dt);
      body.setTransform(new Transform(p.add(newV.mul(dt, new Vector3d()), new Vector3d()), newQ));
    }
  }
}
