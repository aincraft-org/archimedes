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
import dev.mintychochip.phys.Shape;
import dev.mintychochip.phys.Transform;
import java.util.ArrayList;
import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class ShipBody {
  private ShipBody() {}

  public static Body from(
      Ship ship, MaterialKeyResolver resolver, ShipConfig config, int riderCount, Force buoyancy) {
    List<Collider> colliders = new ArrayList<>();
    for (ShipBlock block : ship.blocks()) {
      String key = resolver.key(block);
      double density =
          config.materialDensities().getOrDefault(key, config.defaultMaterialDensity());
      Aabb box = new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
      Transform local =
          new Transform(
              new Vector3d(block.pos().x() + 0.5, block.pos().y() + 0.5, block.pos().z() + 0.5),
              new Quaterniond());
      colliders.add(new SimpleCollider(box, new Material(density), local));
    }
    Vector3d world =
        new Vector3d(ship.origin().x(), ship.origin().y() + ship.pose().y(), ship.origin().z());
    double mass = ShipMassModel.mass(ship, resolver, config, riderCount);
    return new BodyImpl(
        new Transform(world, new Quaterniond()), mass, colliders, List.of(buoyancy));
  }

  private record SimpleCollider(Shape shape, Material material, Transform localTransform)
      implements Collider {}
}
