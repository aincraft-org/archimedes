package dev.mintychochip.archimedes.collision;

import java.util.UUID;

/**
 * An entity that may need hull collision cubes.
 *
 * @param id entity identifier
 * @param player {@code true} when the observer should receive spawn packets
 * @param box world-space bounding box
 */
public record CollisionObserver(UUID id, boolean player, CollisionBox box) {}
