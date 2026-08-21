package dev.mintychochip.archimedes.cannon;

import dev.mintychochip.archimedes.cannon.CannonLauncher.Shot;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipTransform;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Authorizes and resolves cannon firing independently of Bukkit. */
@SuppressWarnings({
  "checkstyle:JavadocVariable",
  "checkstyle:EmptyLineSeparator",
  "checkstyle:IllegalCatch",
  "PMD.AvoidCatchingGenericException"
})
public final class CannonService {
  public static final long COOLDOWN_MILLIS = 2_000L;
  private static final double MUZZLE_OFFSET = 0.75;
  private final CannonLauncher launcher;
  private final Map<CooldownKey, Long> lastFired = new HashMap<>();

  /**
   * Creates a service that fires through {@code launcher}.
   *
   * @param launcher platform projectile adapter
   */
  public CannonService(CannonLauncher launcher) {
    this.launcher = Objects.requireNonNull(launcher, "launcher");
  }

  /**
   * Attempts to fire the cannon whose control is {@code control} on {@code ship}.
   *
   * @param ship captured hull
   * @param control button position in ship space
   * @param requesterId clicking player
   * @param operator whether the player is an operator
   * @param nowMillis current time
   * @return why the shot fired or was refused
   */
  public FireResult fire(
      Vehicle ship, BlockPos control, UUID requesterId, boolean operator, long nowMillis) {
    CannonMount mount = ShipCannons.atControl(ship, control).orElse(null);
    if (mount == null) {
      return FireResult.NOT_A_CANNON;
    }
    if (!operator && !Objects.equals(ship.ownerId(), requesterId)) {
      return FireResult.UNAUTHORIZED;
    }
    CooldownKey key = new CooldownKey(ship.id(), control);
    Long previous = lastFired.get(key);
    if (previous != null && nowMillis - previous < COOLDOWN_MILLIS) {
      return FireResult.COOLDOWN;
    }
    CannonDirection direction = mount.direction();
    ShipTransform.VisualPosition visual = ShipTransform.visual(ship, mount.dispenser());
    Shot shot =
        new Shot(
            ship.id(),
            requesterId,
            visual.x() + 0.5 + direction.dx() * MUZZLE_OFFSET,
            visual.y() + 0.5 + direction.dy() * MUZZLE_OFFSET,
            visual.z() + 0.5 + direction.dz() * MUZZLE_OFFSET,
            direction.dx(),
            direction.dy(),
            direction.dz());
    try {
      launcher.launch(shot);
    } catch (RuntimeException failure) {
      return FireResult.LAUNCH_FAILED;
    }
    lastFired.put(key, nowMillis);
    return FireResult.FIRED;
  }

  /**
   * Drops cooldown state for one ship.
   *
   * @param shipId hull whose cannons should reset
   */
  public void clear(UUID shipId) {
    lastFired.keySet().removeIf(key -> Objects.equals(key.shipId(), shipId));
  }

  /** Drops cooldown state for every ship. */
  public void clearAll() {
    lastFired.clear();
  }

  /** Outcome of an attempted cannon shot. */
  public enum FireResult {
    FIRED,
    NOT_A_CANNON,
    UNAUTHORIZED,
    COOLDOWN,
    LAUNCH_FAILED
  }

  private record CooldownKey(UUID shipId, BlockPos control) {}
}
