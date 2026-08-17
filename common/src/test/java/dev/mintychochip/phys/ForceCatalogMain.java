package dev.mintychochip.phys;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Library-consumer entry: attaches support, Coulomb friction, and linear/angular damping on the
 * shipped engine and prints the defining observables.
 */
public final class ForceCatalogMain {
  private ForceCatalogMain() {}

  public static void main(String[] args) {
    contact();
    staticHold();
    angular();
  }

  private static void contact() {
    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));
    GravityForce gravity = new GravityForce();
    SupportForce support = new SupportForce(floor);
    CoulombFrictionForce friction = new CoulombFrictionForce(floor, 0.5, 0.4);
    BodyImpl withFriction =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(gravity, support, friction));
    BodyImpl withoutFriction =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(gravity, support));
    withFriction.setLinearVelocity(new Vector3d(5, 0, 0));
    withoutFriction.setLinearVelocity(new Vector3d(5, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    new PhysicsEngine().step(world, List.of(withFriction, withoutFriction));
    System.out.printf(
        "contact withVx=%.4f withoutVx=%.4f withVy=%.4f y=%.4f%n",
        withFriction.linearVelocity().x(),
        withoutFriction.linearVelocity().x(),
        withFriction.linearVelocity().y(),
        withFriction.transform().position().y());
  }

  private static void staticHold() {
    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));
    BodyImpl hold =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(
                new GravityForce(),
                new SupportForce(floor),
                new ThrustForce(new Vector3d(1, 0, 0), 8),
                new CoulombFrictionForce(floor, 0.6, 0.4)));
    BodyImpl slip =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(
                new GravityForce(),
                new SupportForce(floor),
                new ThrustForce(new Vector3d(1, 0, 0), 16),
                new CoulombFrictionForce(floor, 0.6, 0.4)));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    new PhysicsEngine().step(world, List.of(hold, slip));
    System.out.printf(
        "static holdVx=%.4f slipVx=%.4f%n", hold.linearVelocity().x(), slip.linearVelocity().x());
  }

  private static void angular() {
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(new AngularDragForce(4), new ViscousDragForce(4)));
    body.setAngularVelocity(new Vector3d(0, 6, 0));
    body.setLinearVelocity(new Vector3d(5, 0, 0));
    double omega0 = body.angularVelocity().length();
    double v0 = body.linearVelocity().length();
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    new PhysicsEngine().step(world, List.of(body));
    System.out.printf(
        "damping omega0=%.4f omega1=%.4f v0=%.4f v1=%.4f%n",
        omega0, body.angularVelocity().length(), v0, body.linearVelocity().length());
  }
}
