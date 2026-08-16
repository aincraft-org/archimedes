package dev.jlo.ships.ship;

/**
 * Unchecked failure used at ship runtime transaction boundaries.
 *
 * <p>Bukkit adapters use this exception to normalize expected unchecked API failures while
 * preserving the original cause. Transaction coordinators use it to trigger rollback and attach
 * cleanup failures as suppressed exceptions.
 */
public final class ShipRuntimeException extends RuntimeException {
  /**
   * Creates a normalized runtime failure.
   *
   * @param message operation-specific failure context
   * @param cause underlying unchecked API or runtime failure
   */
  public ShipRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a runtime failure with the supplied cause.
   *
   * @param cause underlying unchecked API or runtime failure
   */
  public ShipRuntimeException(Throwable cause) {
    super(cause);
  }
}
