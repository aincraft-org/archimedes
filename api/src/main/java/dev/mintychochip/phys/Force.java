package dev.mintychochip.phys;

import org.joml.Vector3dc;

/** A force generator that computes force and torque for a body in a world. */
public interface Force {
  /**
   * Computes this force's contribution for a body.
   *
   * @param body body receiving the force
   * @param world environmental inputs for the computation
   * @return force and torque vectors
   */
  Result apply(Body body, World world);

  /**
   * The force and torque contribution produced by a {@link Force}.
   *
   * @param force force vector
   * @param torque torque vector
   */
  record Result(Vector3dc force, Vector3dc torque) {}
}
