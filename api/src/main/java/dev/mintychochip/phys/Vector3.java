package dev.mintychochip.phys;

public record Vector3(double x, double y, double z) {
  public Vector3 {
    finite(x);
    finite(y);
    finite(z);
  }

  public Vector3 add(Vector3 v) {
    return new Vector3(x + v.x(), y + v.y(), z + v.z());
  }

  public Vector3 subtract(Vector3 v) {
    return new Vector3(x - v.x(), y - v.y(), z - v.z());
  }

  public Vector3 scale(double s) {
    return new Vector3(x * s, y * s, z * s);
  }

  public double dot(Vector3 v) {
    return x * v.x() + y * v.y() + z * v.z();
  }

  public Vector3 cross(Vector3 v) {
    return new Vector3(y * v.z() - z * v.y(), z * v.x() - x * v.z(), x * v.y() - y * v.x());
  }

  public double length() {
    return Math.sqrt(x * x + y * y + z * z);
  }

  static void finite(double value) {
    if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite value");
  }

  /** The zero vector. */
  public static final Vector3 ZERO = new Vector3(0, 0, 0);
}
