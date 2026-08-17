package dev.mintychochip.archimedes.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.collision.CollisionVolumeManager;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests runtime composition transaction boundaries. */
class ShipRuntimeImplTest {
  private static final String COLLISION_MOVE = "collision";
  private static final String COLLISION_ROLLBACK = "collisionRollback";

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

  @Test
  void spawnCollisionFailureAttemptsOnlyCollisionCleanup() {
    RecordingRenderer renderer = new RecordingRenderer();
    RecordingCollision collision = new RecordingCollision();
    collision.spawnFailure = true;
    ShipRuntimeException failure =
        assertThrows(
            ShipRuntimeException.class,
            () -> new ShipRuntimeImpl(renderer, collision).spawn(ship()));
    assertEquals(0, renderer.rendered);
    assertEquals(1, collision.removed);
    assertEquals(0, failure.getSuppressed().length);
  }

  @Test
  void spawnCleanupFailuresAreSuppressedOnPrimaryFailure() {
    RecordingRenderer renderer = new RecordingRenderer();
    renderer.renderFailure = true;
    renderer.removeFailure = true;
    RecordingCollision collision = new RecordingCollision();
    collision.removeFailure = true;
    ShipRuntimeException failure =
        assertThrows(
            ShipRuntimeException.class,
            () -> new ShipRuntimeImpl(renderer, collision).spawn(ship()));
    assertEquals(2, failure.getSuppressed().length);
  }

  @Test
  void upwardCollisionFailureRestoresBasisAfterReverseCarry() {
    RecordingCollision collision = new RecordingCollision();
    collision.moveFailure = true;
    RecordingCarrier carrier = new RecordingCarrier();
    carrier.recordCarryBasis = true;
    Ship ship = ship();
    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(7));

    assertThrows(
        ShipRuntimeException.class,
        () -> new ShipRuntimeImpl(new RecordingRenderer(), collision, carrier).move(ship, 4, 7));

    assertEquals(4.0, carrier.poseBasis);
  }

  @Test
  void upwardCollisionFailureRollsBackRendererModelAndRiders() {
    List<String> operations = new ArrayList<>();
    RecordingRenderer renderer = new RecordingRenderer(operations);
    RecordingCollision collision = new RecordingCollision(operations);
    collision.moveFailure = true;
    RecordingCarrier carrier = new RecordingCarrier(operations);
    Ship ship = ship();
    ShipRuntime runtime = new ShipRuntimeImpl(renderer, collision, carrier);
    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(7));
    assertThrows(RuntimeException.class, () -> runtime.move(ship, 4, 7));

    assertEquals(4.0, ship.pose().y());
    assertEquals(
        List.of(
            "renderer:4.0->7.0",
            "carrier:4.0->7.0",
            COLLISION_MOVE,
            COLLISION_ROLLBACK,
            "carrier:7.0->4.0",
            "renderer:7.0->4.0"),
        operations);
    assertTrue(collision.rolledBack);
    assertEquals(2, carrier.carryCount);
    assertEquals(7.0, carrier.carriedOldY);
    assertEquals(4.0, carrier.carriedNewY);
  }

  @Test
  void upwardMoveCarriesBeforeCollision() {
    List<String> operations = new ArrayList<>();
    RecordingRenderer renderer = new RecordingRenderer(operations);
    RecordingCollision collision = new RecordingCollision(operations);
    RecordingCarrier carrier = new RecordingCarrier(operations);
    Ship ship = ship();
    ShipRuntime runtime = new ShipRuntimeImpl(renderer, collision, carrier);
    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(7));
    runtime.move(ship, 4, 7);

    assertEquals(List.of("renderer:4.0->7.0", "carrier:4.0->7.0", COLLISION_MOVE), operations);
  }

  @Test
  void successfulMoveCommitsCarrierPoseBasis() {
    RecordingCarrier carrier = new RecordingCarrier();
    Ship ship = ship();
    ShipRuntime runtime =
        new ShipRuntimeImpl(new RecordingRenderer(), new RecordingCollision(), carrier);
    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(7));

    runtime.move(ship, 4, 7);

    assertEquals(7.0, carrier.poseBasis);
  }

  @Test
  void downwardMoveMovesCollisionBeforeCarrier() {
    List<String> operations = new ArrayList<>();
    RecordingRenderer renderer = new RecordingRenderer(operations);
    RecordingCollision collision = new RecordingCollision(operations);
    RecordingCarrier carrier = new RecordingCarrier(operations);
    Ship ship = ship();
    ShipRuntime runtime = new ShipRuntimeImpl(renderer, collision, carrier);
    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(4));
    runtime.move(ship, 7, 4);

    assertEquals(List.of("renderer:7.0->4.0", COLLISION_MOVE, "carrier:7.0->4.0"), operations);
  }

  @Test
  void downwardCollisionFailureDoesNotCarry() {
    RecordingRenderer renderer = new RecordingRenderer();
    RecordingCollision collision = new RecordingCollision();
    collision.moveFailure = true;
    RecordingCarrier carrier = new RecordingCarrier();
    Ship ship = ship();
    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(4));

    assertThrows(
        ShipRuntimeException.class,
        () -> new ShipRuntimeImpl(renderer, collision, carrier).move(ship, 7, 4));
    assertEquals(0, carrier.carryCount);
    assertEquals(7.0, ship.pose().y());
  }

  @Test
  void carrierIsCalledForEachBobMove() {
    RecordingRenderer renderer = new RecordingRenderer();
    RecordingCollision collision = new RecordingCollision();
    RecordingCarrier carrier = new RecordingCarrier();
    Ship ship = ship();
    ShipRuntime runtime = new ShipRuntimeImpl(renderer, collision, carrier);
    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(0.0));

    runtime.move(ship, 0.0, 0.1);
    runtime.move(ship, 0.1, -0.05);
    runtime.move(ship, -0.05, 0.0);

    assertEquals(3, carrier.carryCount);
    assertEquals(-0.05, carrier.carriedOldY, 0.0001);
    assertEquals(0.0, carrier.carriedNewY, 0.0001);
  }

  @Test
  void spawnTracksAtCommittedPoseAndRemoveUntracks() {
    RecordingCarrier carrier = new RecordingCarrier();
    Ship ship = ship();
    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(9));
    ShipRuntime runtime =
        new ShipRuntimeImpl(new RecordingRenderer(), new RecordingCollision(), carrier);
    runtime.spawn(ship);
    runtime.remove(ship);
    assertEquals(1, carrier.tracked);
    assertEquals(9.0, carrier.trackedPoseY);
    assertEquals(1, carrier.untracked);
  }

  @Test
  void rendererRemovalFailureStillUntracks() {
    RecordingCarrier carrier = new RecordingCarrier();
    RecordingRenderer renderer = new RecordingRenderer();
    renderer.removeFailure = true;
    ShipRuntime runtime = new ShipRuntimeImpl(renderer, new RecordingCollision(), carrier);

    assertThrows(ShipRuntimeException.class, () -> runtime.remove(ship()));

    assertEquals(1, carrier.untracked);
  }

  @Test
  void collisionRemovalFailureStillUntracks() {
    RecordingCarrier carrier = new RecordingCarrier();
    RecordingCollision collision = new RecordingCollision();
    collision.removeFailure = true;
    ShipRuntime runtime = new ShipRuntimeImpl(new RecordingRenderer(), collision, carrier);

    assertThrows(ShipRuntimeException.class, () -> runtime.remove(ship()));

    assertEquals(1, carrier.untracked);
  }

  @Test
  void removeAllClearsCarrierEvenWhenRegisteredRemovalFails() {
    RecordingCarrier carrier = new RecordingCarrier();
    RecordingRenderer renderer = new RecordingRenderer();
    renderer.removeFailure = true;
    ShipRuntime runtime = new ShipRuntimeImpl(renderer, new RecordingCollision(), carrier);
    assertThrows(RuntimeException.class, () -> runtime.removeAll(List.of(ship())));
    assertEquals(1, carrier.cleared);
  }

  @Test
  void removeAllTaggedDelegatesToAdaptersAndClearsCarrier() {
    RecordingRenderer renderer = new RecordingRenderer();
    RecordingCollision collision = new RecordingCollision();
    RecordingCarrier carrier = new RecordingCarrier();
    new ShipRuntimeImpl(renderer, collision, carrier).removeAllTagged();
    assertEquals(1, renderer.allRuntimeRemoved);
    assertEquals(1, collision.allTaggedRemoved);
    assertEquals(1, carrier.cleared);
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
    int allRuntimeRemoved;
    boolean renderFailure;
    boolean removeFailure;
    private final List<String> operations;

    RecordingRenderer() {
      this(new ArrayList<>());
    }

    RecordingRenderer(List<String> operations) {
      this.operations = operations;
    }

    @Override
    public void render(Ship ship, ShipHolder holder) {
      if (renderFailure) {
        throw new ShipRuntimeException(new IllegalStateException("render"));
      }
      rendered++;
      holder.accept(ship);
    }

    @Override
    public void removeRuntime(Ship ship) {
      if (removeFailure) {
        throw new ShipRuntimeException(new IllegalStateException("renderer cleanup"));
      }
    }

    @Override
    public void reposition(Ship ship, double oldY, double newY) {
      operations.add("renderer:" + oldY + "->" + newY);
    }

    @Override
    public void removeAllRuntime() {
      allRuntimeRemoved++;
    }
  }

  private static final class RecordingCollision implements CollisionVolumeManager {
    int removed;
    int allTaggedRemoved;
    boolean removeFailure;
    boolean spawnFailure;
    boolean moveFailure;
    boolean rolledBack;
    private final List<String> operations;

    RecordingCollision() {
      this(new ArrayList<>());
    }

    RecordingCollision(List<String> operations) {
      this.operations = operations;
    }

    @Override
    public void spawn(Ship ship) {
      if (spawnFailure) {

        throw new ShipRuntimeException(new IllegalStateException(COLLISION_MOVE));
      }
    }

    @Override
    public void move(Ship ship) {
      operations.add(COLLISION_MOVE);
      if (moveFailure) {
        throw new ShipRuntimeException(new IllegalStateException("move"));
      }
    }

    @Override
    public void rollback(Ship ship, double oldY) {
      rolledBack = true;
      operations.add(COLLISION_ROLLBACK);
    }

    @Override
    public void remove(UUID shipId) {
      removed++;
      if (removeFailure) {
        throw new ShipRuntimeException(new IllegalStateException("collision cleanup"));
      }
    }

    @Override
    public void removeAll() {}

    @Override
    public void removeAllTagged() {
      allTaggedRemoved++;
    }
  }

  private static final class RecordingCarrier implements ShipEntityCarrier {
    int carryCount;
    boolean recordCarryBasis;
    int tracked;
    int untracked;
    int cleared;
    double trackedPoseY = Double.NaN;
    double carriedOldY;
    double carriedNewY;
    double poseBasis = Double.NaN;
    private final List<String> operations;

    RecordingCarrier() {
      this(new ArrayList<>());
    }

    RecordingCarrier(List<String> operations) {
      this.operations = operations;
    }

    @Override
    public void track(Ship ship, double poseY) {
      tracked++;
      trackedPoseY = poseY;
      operations.add("track:" + poseY);
    }

    @Override
    public void untrack(Ship ship) {
      untracked++;
    }

    @Override
    public void updatePoseBasis(Ship ship, double poseY) {
      poseBasis = poseY;
    }

    @Override
    public void clear() {
      cleared++;
    }

    @Override
    public void carry(Ship ship, double oldY, double newY) {
      carryCount++;
      operations.add("carrier:" + oldY + "->" + newY);
      carriedOldY = oldY;
      carriedNewY = newY;
      if (recordCarryBasis) {
        poseBasis = oldY;
      }
    }
  }
}
