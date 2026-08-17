package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.phys.Aabb;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.BodyImpl;
import dev.mintychochip.phys.Collider;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.Material;
import dev.mintychochip.phys.Quaternion;
import dev.mintychochip.phys.Shape;
import dev.mintychochip.phys.Transform;
import dev.mintychochip.phys.Vector3;
import java.util.ArrayList;
import java.util.List;

public final class ShipBody {
  private ShipBody() {}

  public static Body from(
      Ship ship, MaterialKeyResolver resolver, ShipConfig config, int riderCount, Force buoyancy) {
    List<Collider> colliders = new ArrayList<>();
    for (ShipBlock block : ship.blocks()) {
      String key = resolver.key(block);
      double density =
          config.materialDensities().getOrDefault(key, config.defaultMaterialDensity());
      Aabb box = new Aabb(Vector3.ZERO, new Vector3(0.5, 0.5, 0.5));
      Transform local =
          new Transform(
              new Vector3(block.pos().x() + 0.5, block.pos().y() + 0.5, block.pos().z() + 0.5),
              new Quaternion(0, 0, 0, 1));
      colliders.add(new SimpleCollider(box, new Material(density), local));
    }
    Vector3 world =
        new Vector3(ship.origin().x(), ship.origin().y() + ship.pose().y(), ship.origin().z());
    double mass = ShipMassModel.mass(ship, resolver, config, riderCount);
    return new BodyImpl(
        new Transform(world, new Quaternion(0, 0, 0, 1)), mass, colliders, List.of(buoyancy));
  }

  private record SimpleCollider(Shape shape, Material material, Transform localTransform)
      implements Collider {}
}
