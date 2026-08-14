package dev.jlo.ships.scan;

/**
 * Immutable world coordinate used as a visited-set key. Uses structural
 * equality so distinct positions never collide.
 */
record CoordKey(int x, int y, int z) {}