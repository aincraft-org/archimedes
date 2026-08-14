package dev.jlo.ships.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jlo.ships.collision.CollisionVolumeManager;
import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests runtime composition transaction boundaries. */
class ShipRuntimeImplTest {
  @Test
  void collisionFailurePreventsRendering() {
    RecordingRenderer renderer = new RecordingRenderer();
    RecordingCollision collision = new RecordingCollision();
    collision.spawnFailure = true;
    ShipRuntime runtime = new ShipRuntimeImpl(renderer, collision);
    assertThrows(RuntimeException.class, () -> runtime.spawn(ship()));
    assertEquals(0, renderer.rendered);
  }

  @Test
  void rendererFailureRemovesCollision() {
    RecordingRenderer renderer = new RecordingRenderer();
    renderer.renderFailure = true;
    RecordingCollision collision = new RecordingCollision();
    ShipRuntime runtime = new ShipRuntimeImpl(renderer, collision);
    assertThrows(RuntimeException.class, () -> runtime.spawn(ship()));
    assertEquals(1, collision.removed);
  }

  private static Ship ship() {
    return new Ship(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 1, 2, 3),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
  }

  private static final class RecordingRenderer implements ShipRendererLike {
    int rendered;
    boolean renderFailure;

    @Override
    public void render(Ship ship, ShipHolder holder) {
      if (renderFailure) {
        throw new IllegalStateException("render");
      }
      rendered++;
      holder.accept(ship);
    }

    @Override
    public void removeRuntime(Ship ship) {}

    @Override
    public void reposition(Ship ship, double oldY, double newY) {}
  }

  private static final class RecordingCollision implements CollisionVolumeManager {
    int removed;
    boolean spawnFailure;

    @Override
    public void spawn(Ship ship) {
      if (spawnFailure) {
        throw new IllegalStateException("collision");
      }
    }

    @Override
    public void move(Ship ship) {}

    @Override
    public void remove(UUID shipId) {
      removed++;
    }

    @Override
    public void removeAll() {}
  }
}
