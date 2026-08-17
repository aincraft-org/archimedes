package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.model.Ship;

public interface ShipPhysics {
  boolean tick(Ship ship);

  boolean rise(Ship ship);

  boolean sink(Ship ship, int blocks);

  void clear(Ship ship);
}
