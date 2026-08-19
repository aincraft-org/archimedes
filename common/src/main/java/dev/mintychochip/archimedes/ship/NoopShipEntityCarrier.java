package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;

/** No-op carrier used when entity carry is not configured. */
public final class NoopShipEntityCarrier implements ShipEntityCarrier {
  /**
   * Shared carrier that intentionally ignores all tracking and carrying requests.
   *
   * <p>Use this instance when entity transport is not configured.
   */
  public static final ShipEntityCarrier INSTANCE = new NoopShipEntityCarrier();

  private NoopShipEntityCarrier() {}

  /** Ignores a request to begin tracking a ship. */
  @Override
  public void track(Vehicle ship, double poseY) {}

  /** Ignores a request to stop tracking a ship. */
  @Override
  public void untrack(Vehicle ship) {}

  /** Clears no state because this carrier tracks nothing. */
  @Override
  public void clear() {}

  /** Ignores a request to carry entities between two ship heights. */
  @Override
  public void carry(Vehicle ship, double oldY, double newY) {
    // nothing to carry
  }

  /** Ignores a request to carry entities between two ship poses. */
  @Override
  public void carry(Vehicle ship, ShipPose from, ShipPose to) {
    // nothing to carry
  }
}
