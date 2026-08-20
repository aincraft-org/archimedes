package dev.mintychochip.archimedes.collision;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paper-free occupancy of exposed hull cells. Observers acquire cells by AABB edge distance, share
 * a live cell, and release it when the last observer leaves.
 */
public final class CollisionStreamer {
  /** Spatial index of exposed cells at pose zero. */
  private final ExposedCellIndex index;

  /** Refcounted observers per live cell. */
  private final CollisionVolumePool pool = new CollisionVolumePool();

  private CollisionStreamer(ExposedCellIndex index) {
    this.index = index;
  }

  /**
   * Builds a streamer for {@code ship}'s exposed cells.
   *
   * @param ship ship whose hull is streamed
   * @return occupancy algorithm
   */
  public static CollisionStreamer of(Vehicle ship) {
    return new CollisionStreamer(ExposedCellIndex.build(ship));
  }

  /**
   * Reconciles live cells against {@code observers} at {@code pose}.
   *
   * @param observers nearby entities that may need cubes
   * @param pose current ship pose
   * @return spawn, despawn, show, and hide actions
   */
  public CollisionVolumePool.Diff observe(Collection<CollisionObserver> observers, ShipPose pose) {
    Map<UUID, Set<BlockPos>> desired = new HashMap<>();
    Set<UUID> players = new HashSet<>();
    for (CollisionObserver observer : observers) {
      desired.put(observer.id(), needed(observer, pose));
      if (observer.player()) {
        players.add(observer.id());
      }
    }
    return pool.reconcile(desired, players);
  }

  /**
   * Returns how many exposed cells the hull has.
   *
   * @return control (A) live count
   */
  public int exposed() {
    return index.size();
  }

  /**
   * Returns how many cells are currently live.
   *
   * @return streamed (B) live count
   */
  public int liveCount() {
    return pool.live().size();
  }

  /**
   * Returns the observer count for {@code cell}.
   *
   * @param cell relative hull cell
   * @return refcount
   */
  public int refcount(BlockPos cell) {
    return pool.refcount(cell);
  }

  /**
   * Returns the cells currently live.
   *
   * @return live relative cells
   */
  public Set<BlockPos> live() {
    return pool.live();
  }

  /**
   * Returns how many live cells {@code playerId} currently observes.
   *
   * @param playerId player observer
   * @return visible cell count
   */
  public int visibleTo(UUID playerId) {
    int visible = 0;
    for (BlockPos cell : pool.live()) {
      if (pool.observers(cell).contains(playerId)) {
        visible++;
      }
    }
    return visible;
  }

  /**
   * Returns the underlying cell index.
   *
   * @return exposed-cell index
   */
  public ExposedCellIndex index() {
    return index;
  }

  private Set<BlockPos> needed(CollisionObserver observer, ShipPose pose) {
    Set<BlockPos> held = heldBy(observer.id());
    Set<BlockPos> enter =
        new HashSet<>(
            index.cellsWithin(
                observer.box(), pose.x(), pose.y(), pose.z(), ExposedCellIndex.ENTER_RANGE));
    Set<BlockPos> leave =
        new HashSet<>(
            index.cellsWithin(
                observer.box(), pose.x(), pose.y(), pose.z(), ExposedCellIndex.LEAVE_RANGE));
    Set<BlockPos> needed = new HashSet<>(enter);
    for (BlockPos cell : held) {
      if (leave.contains(cell)) {
        needed.add(cell);
      }
    }
    return needed;
  }

  private Set<BlockPos> heldBy(UUID observerId) {
    Set<BlockPos> held = new HashSet<>();
    for (BlockPos cell : pool.live()) {
      if (pool.observers(cell).contains(observerId)) {
        held.add(cell);
      }
    }
    return held;
  }
}
