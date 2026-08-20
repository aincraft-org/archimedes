package dev.mintychochip.archimedes.cannon;

import dev.mintychochip.archimedes.cannon.CannonLauncher.Shot;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.util.Vector;

/** Launches a visible, non-incendiary vanilla cannonball. */
@SuppressWarnings({"checkstyle:JavadocVariable", "checkstyle:EmptyLineSeparator"})
public final class BukkitCannonLauncher implements CannonLauncher {
  public static final double PROJECTILE_SPEED = 2.0;
  public static final float EXPLOSION_YIELD = 1.0F;
  private final World world;

  public BukkitCannonLauncher(World world) {
    this.world = Objects.requireNonNull(world, "world");
  }

  @Override
  public void launch(Shot shot) {
    Player shooter = Bukkit.getPlayer(shot.shooterId());
    if (shooter == null || !shooter.isOnline()) {
      throw new IllegalStateException("Cannon shooter is offline");
    }
    Location location = new Location(world, shot.x(), shot.y(), shot.z());
    SmallFireball projectile = world.spawn(location, SmallFireball.class);
    projectile.setShooter(shooter);
    projectile.setIsIncendiary(false);
    projectile.setYield(EXPLOSION_YIELD);
    projectile.setGravity(true);
    projectile.setVelocity(new Vector(shot.dx(), shot.dy(), shot.dz()).multiply(PROJECTILE_SPEED));
  }
}
