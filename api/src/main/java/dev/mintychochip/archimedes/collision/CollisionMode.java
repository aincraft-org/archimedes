package dev.mintychochip.archimedes.collision;

/** Per-ship collision spawn policy. */
public enum CollisionMode {
  /** Spawn one cube per exposed cell, visible to every client. */
  FULL,
  /** Spawn cubes only for cells observers can hit. */
  STREAMED
}
