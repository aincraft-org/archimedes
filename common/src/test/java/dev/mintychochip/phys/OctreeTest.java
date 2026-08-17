package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class OctreeTest {
  @Test
  void queryReturnsOnlyItemsWhoseBoundsOverlapTheRegion() {
    Octree<String> tree = new Octree<>(new Aabb(new Vector3d(), new Vector3d(16, 16, 16)), 2, 6);
    tree.insert("near", box(0, 0, 0, 0.5));
    tree.insert("far", box(10, 0, 0, 0.5));

    List<String> hits = tree.query(box(0, 0, 0, 1));

    assertTrue(hits.contains("near"));
    assertFalse(hits.contains("far"));
  }

  @Test
  void splitStillFindsOverlappingPairs() {
    Octree<Integer> tree = new Octree<>(new Aabb(new Vector3d(), new Vector3d(8, 8, 8)), 1, 6);
    for (int i = 0; i < 8; i++) {
      tree.insert(i, box(i * 0.4, 0, 0, 0.3));
    }

    List<Integer> hits = tree.query(box(0.2, 0, 0, 0.4));

    assertTrue(hits.contains(0));
    assertTrue(hits.contains(1));
    assertFalse(hits.contains(7));
  }

  private static Aabb box(double x, double y, double z, double half) {
    return new Aabb(new Vector3d(x, y, z), new Vector3d(half, half, half));
  }
}
