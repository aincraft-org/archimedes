package dev.mintychochip.archimedes.collision;

/**
 * Point-in-time collision occupancy for inspect and A/B comparison.
 *
 * @param mode current spawn policy
 * @param live currently spawned cubes
 * @param exposed indexed exposed cells
 * @param visibleToPlayer cubes the supplied player currently observes
 */
public record CollisionSnapshot(CollisionMode mode, int live, int exposed, int visibleToPlayer) {}
