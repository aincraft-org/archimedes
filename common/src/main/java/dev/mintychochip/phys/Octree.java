package dev.mintychochip.phys;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Axis-aligned octree for broadphase overlap queries. Items that straddle more than one octant stay
 * on the parent node.
 *
 * @param <T> stored item type
 */
public final class Octree<T> {
  /** Default leaf capacity before a split. */
  private static final int DEFAULT_CAPACITY = 8;

  /** Default maximum subdivision depth. */
  private static final int DEFAULT_MAX_DEPTH = 8;

  /** Root node covering the construction bounds. */
  private final Node root;

  /** Maximum items in a leaf before splitting. */
  private final int capacity;

  /** Maximum tree depth. */
  private final int maxDepth;

  /**
   * Creates an octree with default leaf capacity and depth.
   *
   * @param world root bounds
   */
  public Octree(Bounds world) {
    this(world, DEFAULT_CAPACITY, DEFAULT_MAX_DEPTH);
  }

  /**
   * @param world root bounds
   * @param capacity leaf split threshold
   * @param maxDepth maximum subdivision depth
   */
  public Octree(Bounds world, int capacity, int maxDepth) {
    Objects.requireNonNull(world);
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    if (maxDepth < 1) {
      throw new IllegalArgumentException("maxDepth must be positive");
    }
    this.capacity = capacity;
    this.maxDepth = maxDepth;
    this.root = new Node(world);
  }

  /**
   * Inserts {@code item} with the given axis-aligned bounds.
   *
   * @param item stored value
   * @param bounds item occupancy
   */
  public void insert(T item, Bounds bounds) {
    Objects.requireNonNull(item);
    Objects.requireNonNull(bounds);
    root.insert(new Entry<>(item, bounds), 0);
  }

  /**
   * Returns every inserted item whose bounds overlap {@code region}.
   *
   * @param region query box
   * @return matching items, in visit order
   */
  public List<T> query(Bounds region) {
    Objects.requireNonNull(region);
    List<T> hits = new ArrayList<>();
    root.query(region, hits);
    return hits;
  }

  private final class Node {
    /** Occupancy of this node. */
    private final Bounds bounds;

    /** Items that live here (straddling or unsplit). */
    private final List<Entry<T>> items = new ArrayList<>();

    /** Eight children, or {@code null} while this node is a leaf. */
    private List<Node> children;

    private Node(Bounds bounds) {
      this.bounds = bounds;
    }

    private void insert(Entry<T> entry, int depth) {
      if (children != null) {
        int octant = octant(entry.bounds);
        if (octant >= 0) {
          children.get(octant).insert(entry, depth + 1);
          return;
        }
        items.add(entry);
        return;
      }
      items.add(entry);
      if (items.size() > capacity && depth < maxDepth) {
        subdivide();
        List<Entry<T>> stay = new ArrayList<>();
        for (Entry<T> current : items) {
          int octant = octant(current.bounds);
          if (octant >= 0) {
            children.get(octant).insert(current, depth + 1);
          } else {
            stay.add(current);
          }
        }
        items.clear();
        items.addAll(stay);
      }
    }

    private void query(Bounds region, List<T> hits) {
      if (!bounds.overlaps(region)) {
        return;
      }
      for (Entry<T> entry : items) {
        if (entry.bounds.overlaps(region)) {
          hits.add(entry.item);
        }
      }
      if (children != null) {
        for (Node child : children) {
          child.query(region, hits);
        }
      }
    }

    private void subdivide() {
      Vector3dc min = bounds.min();
      Vector3dc max = bounds.max();
      Vector3d mid =
          new Vector3d(
              (min.x() + max.x()) * 0.5, (min.y() + max.y()) * 0.5, (min.z() + max.z()) * 0.5);
      children = new ArrayList<>(8);
      children.add(new Node(childBox(min.x(), min.y(), min.z(), mid.x(), mid.y(), mid.z())));
      children.add(new Node(childBox(mid.x(), min.y(), min.z(), max.x(), mid.y(), mid.z())));
      children.add(new Node(childBox(min.x(), mid.y(), min.z(), mid.x(), max.y(), mid.z())));
      children.add(new Node(childBox(mid.x(), mid.y(), min.z(), max.x(), max.y(), mid.z())));
      children.add(new Node(childBox(min.x(), min.y(), mid.z(), mid.x(), mid.y(), max.z())));
      children.add(new Node(childBox(mid.x(), min.y(), mid.z(), max.x(), mid.y(), max.z())));
      children.add(new Node(childBox(min.x(), mid.y(), mid.z(), mid.x(), max.y(), max.z())));
      children.add(new Node(childBox(mid.x(), mid.y(), mid.z(), max.x(), max.y(), max.z())));
    }

    private int octant(Bounds item) {
      if (children == null) {
        return -1;
      }
      int found = -1;
      for (int i = 0; i < children.size(); i++) {
        if (contains(children.get(i).bounds, item)) {
          if (found >= 0) {
            return -1;
          }
          found = i;
        }
      }
      return found;
    }
  }

  private static boolean contains(Bounds outer, Bounds inner) {
    return inner.min().x() >= outer.min().x()
        && inner.max().x() <= outer.max().x()
        && inner.min().y() >= outer.min().y()
        && inner.max().y() <= outer.max().y()
        && inner.min().z() >= outer.min().z()
        && inner.max().z() <= outer.max().z();
  }

  private static Aabb childBox(
      double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    Vector3d center = new Vector3d((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
    Vector3d half = new Vector3d((maxX - minX) * 0.5, (maxY - minY) * 0.5, (maxZ - minZ) * 0.5);
    return new Aabb(center, half);
  }

  private record Entry<T>(T item, Bounds bounds) {}
}
