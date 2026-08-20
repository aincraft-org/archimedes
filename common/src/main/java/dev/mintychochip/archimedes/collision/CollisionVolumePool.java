package dev.mintychochip.archimedes.collision;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Refcounted set of observers per exposed hull cell. Reconcile diffs the desired occupancy against
 * the live occupancy and records spawn, despawn, show, and hide actions.
 */
public final class CollisionVolumePool {
  /** Live observers keyed by relative cell. */
  private final Map<BlockPos, Set<UUID>> observers = new HashMap<>();

  /**
   * Applies the desired observer occupancy and returns the actions needed to match it.
   *
   * @param desired cells needed by each observer
   * @param players observer ids that should receive client packets
   * @return spawn, despawn, show, and hide sets
   */
  public Diff reconcile(Map<UUID, Set<BlockPos>> desired, Set<UUID> players) {
    Map<BlockPos, Set<UUID>> next = invert(desired);
    Set<BlockPos> spawn = new HashSet<>();
    Set<BlockPos> despawn = new HashSet<>();
    Map<BlockPos, Set<UUID>> show = new HashMap<>();
    Map<BlockPos, Set<UUID>> hide = new HashMap<>();

    for (Map.Entry<BlockPos, Set<UUID>> entry : next.entrySet()) {
      BlockPos cell = entry.getKey();
      Set<UUID> incoming = entry.getValue();
      Set<UUID> current = observers.getOrDefault(cell, Set.of());
      if (current.isEmpty()) {
        spawn.add(cell);
      }
      Set<UUID> addedPlayers = playersOf(difference(incoming, current), players);
      if (!addedPlayers.isEmpty()) {
        show.put(cell, Set.copyOf(addedPlayers));
      }
    }

    for (Map.Entry<BlockPos, Set<UUID>> entry : observers.entrySet()) {
      BlockPos cell = entry.getKey();
      Set<UUID> current = entry.getValue();
      Set<UUID> incoming = next.getOrDefault(cell, Set.of());
      if (incoming.isEmpty()) {
        despawn.add(cell);
      }
      Set<UUID> removedPlayers = playersOf(difference(current, incoming), players);
      if (!removedPlayers.isEmpty()) {
        hide.put(cell, Set.copyOf(removedPlayers));
      }
    }

    observers.clear();
    for (Map.Entry<BlockPos, Set<UUID>> entry : next.entrySet()) {
      observers.put(entry.getKey(), new HashSet<>(entry.getValue()));
    }
    return new Diff(Set.copyOf(spawn), Set.copyOf(despawn), copyNested(show), copyNested(hide));
  }

  /**
   * Returns the cells that currently have at least one observer.
   *
   * @return live relative cells
   */
  public Set<BlockPos> live() {
    return Set.copyOf(observers.keySet());
  }

  /**
   * Returns the observer count for {@code cell}, or {@code 0} when it is not live.
   *
   * @param cell relative hull cell
   * @return observer count
   */
  public int refcount(BlockPos cell) {
    Set<UUID> current = observers.get(cell);
    return current == null ? 0 : current.size();
  }

  /**
   * Returns the observers currently holding {@code cell}.
   *
   * @param cell relative hull cell
   * @return observer ids
   */
  public Set<UUID> observers(BlockPos cell) {
    Set<UUID> current = observers.get(cell);
    return current == null ? Set.of() : Set.copyOf(current);
  }

  private static Map<BlockPos, Set<UUID>> invert(Map<UUID, Set<BlockPos>> desired) {
    Map<BlockPos, Set<UUID>> next = new HashMap<>();
    for (Map.Entry<UUID, Set<BlockPos>> entry : desired.entrySet()) {
      for (BlockPos cell : entry.getValue()) {
        next.computeIfAbsent(cell, ignored -> new HashSet<>()).add(entry.getKey());
      }
    }
    return next;
  }

  private static Set<UUID> difference(Set<UUID> left, Set<UUID> right) {
    Set<UUID> result = new HashSet<>(left);
    result.removeAll(right);
    return result;
  }

  private static Set<UUID> playersOf(Set<UUID> ids, Set<UUID> players) {
    Set<UUID> result = new HashSet<>(ids);
    result.retainAll(players);
    return result;
  }

  private static Map<BlockPos, Set<UUID>> copyNested(Map<BlockPos, Set<UUID>> source) {
    Map<BlockPos, Set<UUID>> copy = new HashMap<>();
    for (Map.Entry<BlockPos, Set<UUID>> entry : source.entrySet()) {
      copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
    }
    return Map.copyOf(copy);
  }

  /**
   * Pool occupancy changes from one reconcile to the next.
   *
   * @param spawn cells that became live
   * @param despawn cells that became empty
   * @param show players that should start receiving each cell
   * @param hide players that should stop receiving each cell
   */
  public record Diff(
      Set<BlockPos> spawn,
      Set<BlockPos> despawn,
      Map<BlockPos, Set<UUID>> show,
      Map<BlockPos, Set<UUID>> hide) {}
}
