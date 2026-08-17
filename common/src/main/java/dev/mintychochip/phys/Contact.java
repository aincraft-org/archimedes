package dev.mintychochip.phys;

import org.joml.Vector3dc;

/**
 * An AABB contact produced by the octree broadphase.
 *
 * @param a first body
 * @param b second body
 * @param normal unit axis from {@code a} toward {@code b}
 * @param penetration overlap along {@code normal}
 */
public record Contact(Body a, Body b, Vector3dc normal, double penetration) {}
