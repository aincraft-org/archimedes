package dev.mintychochip.phys;

public record Quaternion(double x, double y, double z, double w) {
  public Quaternion { Vector3.finite(x); Vector3.finite(y); Vector3.finite(z); Vector3.finite(w); if (Math.abs(Math.sqrt(x*x+y*y+z*z+w*w)-1.0)>1e-9) throw new IllegalArgumentException("quaternion must be normalized"); }
  public Vector3 rotate(Vector3 v) {
    Vector3 u = new Vector3(x, y, z);
    Vector3 t1 = u.cross(v);
    Vector3 t2 = u.cross(t1.add(v.scale(w)));
    return v.add(t2.scale(2));
  }
}
