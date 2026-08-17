package dev.mintychochip.phys;

import java.util.Objects;

public record Transform(Vector3 position, Quaternion orientation) {
  public Transform {
    Objects.requireNonNull(position);
    Objects.requireNonNull(orientation);
  }
}
