package dev.mintychochip.phys;

import java.util.Collection;
import java.util.Objects;

public final class PhysicsEngine implements Physics {
  public void step(World world, Collection<Body> bodies) {
    Objects.requireNonNull(world);
    Objects.requireNonNull(bodies);
    double dt = world.timeStep();
    if (!Double.isFinite(dt) || dt < 0) throw new IllegalArgumentException("bad timestep");
    for (Body body : bodies) {
      if (!body.active()) continue;
      Vector3 totalForce = Vector3.ZERO;
      Vector3 totalTorque = Vector3.ZERO;
      for (Force force : body.forces()) {
        Force.Result r = force.apply(body, world);
        totalForce = totalForce.add(r.force());
        totalTorque = totalTorque.add(r.torque());
      }
      Vector3 acc = totalForce.scale(body.inverseMass());
      Vector3 v = body.linearVelocity();
      Vector3 newV = v.add(acc.scale(dt));
      body.setLinearVelocity(newV);

      Vector3 angularAcc = body.inverseInertia().multiply(totalTorque);
      Vector3 omega = body.angularVelocity();
      Vector3 newOmega = omega.add(angularAcc.scale(dt));
      body.setAngularVelocity(newOmega);

      Vector3 p = body.transform().position();
      Quaternion q = body.transform().orientation();
      Quaternion newQ = integrateOrientation(q, newOmega, dt);
      body.setTransform(new Transform(p.add(newV.scale(dt)), newQ));
    }
  }

  private static Quaternion integrateOrientation(Quaternion q, Vector3 omega, double dt) {
    if (omega.x() == 0 && omega.y() == 0 && omega.z() == 0) return q;
    double ox = omega.x(), oy = omega.y(), oz = omega.z();
    double rx = q.w() * ox + q.y() * oz - q.z() * oy;
    double ry = q.w() * oy - q.x() * oz + q.z() * ox;
    double rz = q.w() * oz + q.x() * oy - q.y() * ox;
    double rw = -q.x() * ox - q.y() * oy - q.z() * oz;
    double k = 0.5 * dt;
    double nx = q.x() + k * rx;
    double ny = q.y() + k * ry;
    double nz = q.z() + k * rz;
    double nw = q.w() + k * rw;
    double norm = Math.sqrt(nx * nx + ny * ny + nz * nz + nw * nw);
    return new Quaternion(nx / norm, ny / norm, nz / norm, nw / norm);
  }
}
