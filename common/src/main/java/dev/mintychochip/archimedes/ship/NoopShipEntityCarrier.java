package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.Ship;

/** No-op carrier used when entity carry is not configured. */
public final class NoopShipEntityCarrier implements ShipEntityCarrier {
  /** Shared no-op carrier instance. */
  public static final ShipEntityCarrier INSTANCE = new NoopShipEntityCarrier();

  private NoopShipEntityCarrier() {}

  @Override
  public void track(Ship ship, double poseY) {}

  @Override
  public void untrack(Ship ship) {}

  @Override
  public void clear() {}

  @Override
  public void carry(Ship ship, double oldY, double newY) {
    // nothing to carry
  }
}
