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
   * Separates overlapping bodies along the contact normal and kills closing speed.
   *
   * @param contacts contacts from {@link #detect(Collection)}
   */
  public static void resolve(List<Contact> contacts) {
    Objects.requireNonNull(contacts);
    for (Contact contact : contacts) {
      separate(contact);
    }
  }

  private static void separate(Contact contact) {
    double wa = contact.a().active() ? contact.a().inverseMass() : 0;
    double wb = contact.b().active() ? contact.b().inverseMass() : 0;
    double sum = wa + wb;
    if (sum == 0) {
      return;
    }
    Vector3d normal = new Vector3d(contact.normal());
    if (contact.a().active()) {
      shift(contact.a(), normal, -contact.penetration() * (wa / sum));
    }
    if (contact.b().active()) {
      shift(contact.b(), normal, contact.penetration() * (wb / sum));
    }
    Vector3d relative =
        new Vector3d(contact.b().linearVelocity()).sub(contact.a().linearVelocity());
    double closing = relative.dot(normal);
    if (closing >= 0) {
      return;
    }
    if (contact.a().active()) {
      contact
          .a()
          .setLinearVelocity(
              new Vector3d(contact.a().linearVelocity())
                  .add(normal.mul(closing * (wa / sum), new Vector3d())));
    }
    if (contact.b().active()) {
      contact
          .b()
          .setLinearVelocity(
              new Vector3d(contact.b().linearVelocity())
                  .sub(normal.mul(closing * (wb / sum), new Vector3d())));
    }
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
    double ox = Math.min(ba.max().x(), bb.max().x()) - Math.max(ba.min().x(), bb.min().x());
    double oy = Math.min(ba.max().y(), bb.max().y()) - Math.max(ba.min().y(), bb.min().y());
    double oz = Math.min(ba.max().z(), bb.max().z()) - Math.max(ba.min().z(), bb.min().z());
    if (ox <= 0 || oy <= 0 || oz <= 0) {
      return null;
    }
    Vector3d centerA = center(ba);
    Vector3d centerB = center(bb);
    if (ox <= oy && ox <= oz) {
      double sign = centerB.x() >= centerA.x() ? 1 : -1;
      return new Contact(a, b, new Vector3d(sign, 0, 0), ox);
    }
    if (oy <= oz) {
      double sign = centerB.y() >= centerA.y() ? 1 : -1;
      return new Contact(a, b, new Vector3d(0, sign, 0), oy);
    }
    double sign = centerB.z() >= centerA.z() ? 1 : -1;
    return new Contact(a, b, new Vector3d(0, 0, sign), oz);
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
    Vector3d world =
        body.transform().position().add(collider.localTransform().position(), new Vector3d());
    return collider.shape().bounds(new Transform(world, collider.localTransform().orientation()));
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
