package dev.jlo.ships.collision;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

/** In-memory debug fixture lifecycle. */
public final class CollisionDebugServiceImpl implements CollisionDebugService {
  /** Collision volume factory. */
  private final CollisionVolumeManager manager;

  /** World containing the fixture. */
  private final World world;

  /** Active fixtures keyed by player. */
  private final Map<UUID, Fixture> fixtures = new HashMap<>();

  /**
   * Creates the debug service.
   *
   * @param manager collision volume manager
   * @param world fixture world
   */
  public CollisionDebugServiceImpl(CollisionVolumeManager manager, World world) {
    this.manager = manager;
    this.world = world;
  }

  public CollisionVolume spawn(UUID playerId, int x, int y, int z) {
    remove(playerId);
    throw new UnsupportedOperationException("Debug collision fixtures are no longer supported");
  }

  @Override
  public boolean move(UUID playerId, int dy) {
    return false;
  }

  @Override
  public boolean remove(UUID playerId) {
    fixtures.remove(playerId);
    return false;
  }

  @Override
  public void removeAll() {
    for (UUID playerId : java.util.Set.copyOf(fixtures.keySet())) {
      remove(playerId);
    }
  }

  /** Stored debug fixture state. */
  private static final class Fixture {
    /** Collision volume. */
    private final CollisionVolume volume;

    /** Fixed x coordinate. */
    private final int x;

    /** Mutable y coordinate. */
    private int y;

    /** Fixed z coordinate. */
    private final int z;

    private Fixture(CollisionVolume volume, int x, int y, int z) {
      this.volume = volume;
      this.x = x;
      this.y = y;
      this.z = z;
    }
  }
}
