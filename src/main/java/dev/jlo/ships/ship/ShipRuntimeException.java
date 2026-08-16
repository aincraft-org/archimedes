package dev.jlo.ships.ship;

/** Runtime failure raised while composing or rolling back ship runtime components. */
public final class ShipRuntimeException extends RuntimeException {
  /** Creates a normalized runtime failure. */
  public ShipRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a runtime failure with the supplied cause.
   *
   * @param cause underlying runtime failure
   */
  public ShipRuntimeException(Throwable cause) {
    super(cause);
  }
}
