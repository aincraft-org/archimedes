package dev.jlo.ships.scan;

/**
 * Immutable world coordinate used as a visited-set key. Uses structural equality so distinct
 * positions never collide.
 *
 * @param x the x coordinate
 * @param y the y coordinate
 * @param z the z coordinate
 */
record CoordKey(int x, int y, int z) {}
