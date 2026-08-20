package dev.mintychochip.archimedes.bukkit;

import dev.mintychochip.archimedes.model.Vehicle;
import java.util.Collection;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Refreshes streamed collision volumes when potential observers move across a block boundary or
 * leave the world.
 */
public final class BukkitCollisionObserverListener implements Listener {
  /** Live ships. */
  private final Supplier<Collection<Vehicle>> ships;

  /** Collision manager that owns streamed volumes. */
  private final BukkitCollisionVolumeManager collisions;

  /**
   * Creates the listener.
   *
   * @param ships live ships
   * @param collisions collision manager
   */
  public BukkitCollisionObserverListener(
      Supplier<Collection<Vehicle>> ships, BukkitCollisionVolumeManager collisions) {
    this.ships = ships;
    this.collisions = collisions;
  }

  /**
   * Reconciles when a player crosses a block boundary.
   *
   * @param event movement event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerMove(PlayerMoveEvent event) {
    Location from = event.getFrom();
    Location to = event.getTo();
    if (to == null
        || from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) {
      return;
    }
    reconcileAll();
  }

  /**
   * Reconciles after a player teleport.
   *
   * @param event teleport event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerTeleport(PlayerTeleportEvent event) {
    reconcileAll();
  }

  /**
   * Reconciles after a player quits.
   *
   * @param event quit event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerQuit(PlayerQuitEvent event) {
    reconcileAll();
  }

  /**
   * Reconciles after an item appears near a hull.
   *
   * @param event item spawn event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onItemSpawn(ItemSpawnEvent event) {
    reconcileAll();
  }

  /**
   * Reconciles after an entity dies.
   *
   * @param event death event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onEntityDeath(EntityDeathEvent event) {
    reconcileAll();
  }

  private void reconcileAll() {
    for (Vehicle ship : ships.get()) {
      collisions.reconcileObservers(ship);
    }
  }
}
