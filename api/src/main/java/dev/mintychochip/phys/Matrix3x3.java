package dev.mintychochip.phys;

public record Matrix3x3(
    double m00, double m01, double m02,
    double m10, double m11, double m12,
    double m20, double m21, double m22) {
  public Matrix3x3 {
    double[] values = {m00, m01, m02, m10, m11, m12, m20, m21, m22};
    for (double value : values) {
      Vector3.finite(value);
    }
  }

  public Vector3 multiply(Vector3 v) {
    return new Vector3(
        m00 * v.x() + m01 * v.y() + m02 * v.z(),
        m10 * v.x() + m11 * v.y() + m12 * v.z(),
        m20 * v.x() + m21 * v.y() + m22 * v.z());
  }
}
