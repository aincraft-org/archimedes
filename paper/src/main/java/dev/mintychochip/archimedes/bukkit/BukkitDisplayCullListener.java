package dev.mintychochip.archimedes.bukkit;

import dev.mintychochip.archimedes.model.Vehicle;
import java.util.Collection;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Reapplies display line-of-sight culling when a viewer moves across a block. */
public final class BukkitDisplayCullListener implements Listener {
  /** Live ships. */
  private final Supplier<Collection<Vehicle>> ships;

  /** Renderer that owns display entities. */
  private final BukkitShipRenderer renderer;

  /**
   * Creates the listener.
   *
   * @param ships live ships
   * @param renderer display renderer
   */
  public BukkitDisplayCullListener(
      Supplier<Collection<Vehicle>> ships, BukkitShipRenderer renderer) {
    this.ships = ships;
    this.renderer = renderer;
  }

  /**
   * Culls when a player crosses a block boundary.
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
    cullAll();
  }

  /**
   * Culls after teleport.
   *
   * @param event teleport event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerTeleport(PlayerTeleportEvent event) {
    cullAll();
  }

  /**
   * Culls after quit so leftover show packets are not kept for a gone viewer.
   *
   * @param event quit event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerQuit(PlayerQuitEvent event) {
    cullAll();
  }

  private void cullAll() {
    for (Vehicle ship : ships.get()) {
      renderer.cullViewers(ship);
    }
  }
}
