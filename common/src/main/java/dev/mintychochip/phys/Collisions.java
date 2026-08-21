package dev.mintychochip.phys;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Octree broadphase and AABB depenetration for rigid bodies. */
public final class Collisions {
  /** Extra margin around the union of all body bounds used as the octree root. */
  private static final double ROOT_PAD = 1.0;

  /**
   * Per-axis cap on world-voxel cells visited for one collider. Integer conversion saturates at
   * {@link Integer#MAX_VALUE}, so an exploded AABB must not wrap the cell loop.
   */
  private static final int MAX_WORLD_CELLS_PER_AXIS = 8;

  private Collisions() {}

  /**
   * Finds interpenetrating body pairs using an octree of collider AABBs.
   *
   * @param bodies candidates
   * @return one contact per overlapping pair (deepest collider overlap)
   */
  public static List<Contact> detect(Collection<Body> bodies) {
    Objects.requireNonNull(bodies);
    List<Body> collidable = new ArrayList<>();
    List<Bounds> bounds = new ArrayList<>();
    Bounds world = null;
    for (Body body : bodies) {
      Bounds box = worldBounds(body);
      if (box == null) {
        continue;
      }
      collidable.add(body);
      bounds.add(box);
      world = world == null ? box : enclose(world, box);
    }
    if (collidable.size() < 2 || world == null) {
      return List.of();
    }
    Octree<Integer> tree = new Octree<>(pad(world));
    for (int i = 0; i < collidable.size(); i++) {
      tree.insert(i, bounds.get(i));
    }
    Map<Body, Body> seen = new IdentityHashMap<>();
    List<Contact> contacts = new ArrayList<>();
    for (int i = 0; i < collidable.size(); i++) {
      Body a = collidable.get(i);
      for (Integer j : tree.query(bounds.get(i))) {
        if (j <= i) {
          continue;
        }
        Body b = collidable.get(j);
        if (seen.get(a) == b || seen.get(b) == a) {
          continue;
        }
        Contact contact = narrow(a, b);
        if (contact != null) {
          seen.put(a, b);
          contacts.add(contact);
        }
      }
    }
    return contacts;
  }

  /**
   * Finds contacts between active bodies and solid world voxels overlapping their collider AABBs.
   *
   * <p>Colliders whose center is already inside a solid voxel are skipped so a fully buried hull is
   * left to path clearance instead of generating a contact storm.
   *
   * @param world occupancy and fluid field
   * @param bodies candidates
   * @return the deepest contact per collider against overlapping solid voxels
   */
  public static List<Contact> detectWorld(World world, Collection<Body> bodies) {
    Objects.requireNonNull(world);
    Objects.requireNonNull(bodies);
    List<Contact> contacts = new ArrayList<>();
    Vector3d voxelHalf = new Vector3d(0.5, 0.5, 0.5);
    for (Body body : bodies) {
      if (!body.active() || body.colliders().isEmpty()) {
        continue;
      }
      for (Collider collider : body.colliders()) {
        Bounds box = colliderBounds(body, collider);
        Vector3d colliderCenter = new Vector3d(box.min()).add(box.max(), new Vector3d()).mul(0.5);
        if (world.isObstacle(colliderCenter) && !world.fluidField().isFluid(colliderCenter)) {
          continue;
        }
        int minX = (int) Math.floor(box.min().x());
        int maxX = (int) Math.floor(box.max().x() - 1e-9);
        int minY = (int) Math.floor(box.min().y());
        int maxY = (int) Math.floor(box.max().y() - 1e-9);
        int minZ = (int) Math.floor(box.min().z());
        int maxZ = (int) Math.floor(box.max().z() - 1e-9);
        if (maxX < minX
            || maxY < minY
            || maxZ < minZ
            || (long) maxX - minX >= MAX_WORLD_CELLS_PER_AXIS
            || (long) maxY - minY >= MAX_WORLD_CELLS_PER_AXIS
            || (long) maxZ - minZ >= MAX_WORLD_CELLS_PER_AXIS) {
          continue;
        }
        Contact best = null;
        for (int x = minX; x <= maxX; x++) {
          for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
              Vector3d p = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
              if (!world.isObstacle(p) || world.fluidField().isFluid(p)) {
                continue;
              }
              Contact contact = aabbContact(body, null, box, new Aabb(p, voxelHalf));
              if (contact != null && (best == null || contact.penetration() > best.penetration())) {
                best = contact;
              }
            }
          }
        }
        if (best != null) {
          contacts.add(best);
        }
      }
    }
    return contacts;
  }

  /**
   * Separates overlapping bodies along the contact normal and kills closing speed.
   *
   * @param contacts contacts from {@link #detect(Collection)} or {@link #detectWorld(World,
   *     Collection)}
   */
  public static void resolve(List<Contact> contacts) {
    Objects.requireNonNull(contacts);
    for (Contact contact : contacts) {
      separate(contact);
    }
  }

  private static void separate(Contact contact) {
    double wa = contact.a().active() ? contact.a().inverseMass() : 0;
    double wb = infinite(contact.b()) ? 0 : contact.b().inverseMass();
    double sum = wa + wb;
    if (sum == 0) {
      return;
    }
    Vector3d normal = new Vector3d(contact.normal());
    if (contact.a().active()) {
      shift(contact.a(), normal, -contact.penetration() * (wa / sum));
    }
    if (!infinite(contact.b())) {
      shift(contact.b(), normal, contact.penetration() * (wb / sum));
    }
    applyVelocityImpulse(contact, normal);
  }

  private static void applyVelocityImpulse(Contact contact, Vector3dc normal) {
    Vector3d ra = radius(contact.a(), contact.point());
    Vector3d relative = new Vector3d(pointVelocity(contact.a(), ra)).negate();
    Vector3d rb = new Vector3d();
    if (!infinite(contact.b())) {
      rb = radius(contact.b(), contact.point());
      relative.add(pointVelocity(contact.b(), rb));
    }
    double closing = relative.dot(normal);
    if (closing >= 0) {
      return;
    }
    double ka = effectiveInvMass(contact.a(), ra, normal);
    double kb = infinite(contact.b()) ? 0 : effectiveInvMass(contact.b(), rb, normal);
    double denom = ka + kb;
    if (denom == 0) {
      return;
    }
    Vector3d impulse = new Vector3d(normal).mul(closing / denom);
    applyImpulse(contact.a(), ra, impulse, 1.0);
    applyImpulse(contact.b(), rb, impulse, -1.0);
  }

  private static boolean infinite(Body body) {
    return body == null || !body.active();
  }

  private static Vector3d radius(Body body, Vector3dc point) {
    return new Vector3d(point).sub(MassProperties.worldCenterOfMass(body));
  }

  private static Vector3d pointVelocity(Body body, Vector3dc radius) {
    Vector3d tangential = new Vector3d(body.angularVelocity()).cross(radius, new Vector3d());
    return new Vector3d(body.linearVelocity()).add(tangential);
  }

  private static double effectiveInvMass(Body body, Vector3dc radius, Vector3dc normal) {
    if (!body.active()) {
      return 0;
    }
    Vector3d rXn = new Vector3d(radius).cross(normal, new Vector3d());
    return body.inverseMass() + rXn.dot(body.inverseInertia().transform(rXn, new Vector3d()));
  }

  private static void applyImpulse(Body body, Vector3dc radius, Vector3dc impulse, double sign) {
    if (body == null || !body.active()) {
      return;
    }
    body.setLinearVelocity(new Vector3d(body.linearVelocity()).fma(sign, impulse));
    Vector3d angularImpulse = new Vector3d(radius).cross(impulse, new Vector3d()).mul(sign);
    Vector3d deltaOmega = body.inverseInertia().transform(angularImpulse, new Vector3d());
    body.setAngularVelocity(new Vector3d(body.angularVelocity()).add(deltaOmega));
  }

  private static void shift(Body body, Vector3dc normal, double distance) {
    Vector3d position =
        new Vector3d(body.transform().position()).add(new Vector3d(normal).mul(distance));
    body.setTransform(new Transform(position, body.transform().orientation()));
  }

  private static Contact narrow(Body a, Body b) {
    Contact best = null;
    for (Collider ca : a.colliders()) {
      Bounds ba = colliderBounds(a, ca);
      for (Collider cb : b.colliders()) {
        Bounds bb = colliderBounds(b, cb);
        Contact candidate = aabbContact(a, b, ba, bb);
        if (candidate != null && (best == null || candidate.penetration() > best.penetration())) {
          best = candidate;
        }
      }
    }
    return best;
  }

  private static Contact aabbContact(Body a, Body b, Bounds ba, Bounds bb) {
    double minX = Math.max(ba.min().x(), bb.min().x());
    double minY = Math.max(ba.min().y(), bb.min().y());
    double minZ = Math.max(ba.min().z(), bb.min().z());
    double maxX = Math.min(ba.max().x(), bb.max().x());
    double maxY = Math.min(ba.max().y(), bb.max().y());
    double maxZ = Math.min(ba.max().z(), bb.max().z());
    double ox = maxX - minX;
    double oy = maxY - minY;
    double oz = maxZ - minZ;
    if (ox <= 0 || oy <= 0 || oz <= 0) {
      return null;
    }
    Vector3d point = new Vector3d(minX + maxX, minY + maxY, minZ + maxZ).mul(0.5);
    Vector3d centerA = center(ba);
    Vector3d centerB = center(bb);
    if (ox <= oy && ox <= oz) {
      double sign = centerB.x() >= centerA.x() ? 1 : -1;
      return contact(a, b, point, new Vector3d(sign, 0, 0), ox);
    }
    if (oy <= oz) {
      double sign = centerB.y() >= centerA.y() ? 1 : -1;
      return contact(a, b, point, new Vector3d(0, sign, 0), oy);
    }
    double sign = centerB.z() >= centerA.z() ? 1 : -1;
    return contact(a, b, point, new Vector3d(0, 0, sign), oz);
  }

  private static Contact contact(
      Body a, Body b, Vector3dc point, Vector3dc normal, double penetration) {
    if (b == null) {
      return Contact.world(a, point, normal, penetration);
    }
    return new Contact(a, b, point, normal, penetration);
  }

  private static Bounds worldBounds(Body body) {
    Bounds acc = null;
    for (Collider collider : body.colliders()) {
      Bounds box = colliderBounds(body, collider);
      acc = acc == null ? box : enclose(acc, box);
    }
    return acc;
  }

  private static Bounds colliderBounds(Body body, Collider collider) {
    return collider.shape().bounds(body.transform().compose(collider.localTransform()));
  }

  private static Aabb enclose(Bounds a, Bounds b) {
    Vector3d min =
        new Vector3d(
            Math.min(a.min().x(), b.min().x()),
            Math.min(a.min().y(), b.min().y()),
            Math.min(a.min().z(), b.min().z()));
    Vector3d max =
        new Vector3d(
            Math.max(a.max().x(), b.max().x()),
            Math.max(a.max().y(), b.max().y()),
            Math.max(a.max().z(), b.max().z()));
    Vector3d center = new Vector3d(min).add(max).mul(0.5);
    Vector3d half = new Vector3d(max).sub(min).mul(0.5);
    return new Aabb(center, half);
  }

  private static Aabb pad(Bounds box) {
    Vector3d center = center(box);
    Vector3d half =
        new Vector3d(
            (box.max().x() - box.min().x()) * 0.5 + ROOT_PAD,
            (box.max().y() - box.min().y()) * 0.5 + ROOT_PAD,
            (box.max().z() - box.min().z()) * 0.5 + ROOT_PAD);
    return new Aabb(center, half);
  }

  private static Vector3d center(Bounds box) {
    return new Vector3d(box.min()).add(box.max()).mul(0.5);
  }
}
