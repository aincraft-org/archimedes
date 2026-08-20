package dev.mintychochip.archimedes.cannon;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.Objects;

/**
 * A dispenser cannon and its rendered interaction control.
 *
 * @param dispenser captured dispenser position
 * @param control captured button position
 * @param direction dispenser firing direction
 */
public record CannonMount(BlockPos dispenser, BlockPos control, CannonDirection direction) {
  public CannonMount {
    Objects.requireNonNull(dispenser, "dispenser");
    Objects.requireNonNull(control, "control");
    Objects.requireNonNull(direction, "direction");
  }
}
