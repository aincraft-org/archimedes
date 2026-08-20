package dev.mintychochip.archimedes.cannon;

import dev.mintychochip.archimedes.cannon.BukkitCannonDisplay.DisplayTarget;
import dev.mintychochip.archimedes.cannon.CannonService.FireResult;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.archimedes.ship.ShipService;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/** Routes right-clicks on tagged rendered cannon controls into the cannon domain. */
@SuppressWarnings({
  "checkstyle:JavadocVariable",
  "checkstyle:EmptyLineSeparator",
  "checkstyle:MissingSwitchDefault"
})
public final class BukkitCannonInteractionListener implements Listener {
  private final ShipService ships;
  private final CannonService cannons;
  private final NamespacedKey shipKey;
  private final NamespacedKey blockKey;
  private final LongSupplier clock;
  private final Logger logger;

  public BukkitCannonInteractionListener(
      ShipService ships,
      CannonService cannons,
      NamespacedKey shipKey,
      NamespacedKey blockKey,
      LongSupplier clock,
      Logger logger) {
    this.ships = Objects.requireNonNull(ships, "ships");
    this.cannons = Objects.requireNonNull(cannons, "cannons");
    this.shipKey = Objects.requireNonNull(shipKey, "shipKey");
    this.blockKey = Objects.requireNonNull(blockKey, "blockKey");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  @EventHandler
  public void onInteract(PlayerInteractEntityEvent event) {
    DisplayTarget target =
        BukkitCannonDisplay.read(event.getRightClicked(), shipKey, blockKey).orElse(null);
    if (target == null) {
      return;
    }
    Vehicle ship =
        ships.all().stream()
            .filter(candidate -> candidate.id().equals(target.shipId()))
            .findFirst()
            .orElse(null);
    if (ship == null) {
      return;
    }
    Player player = event.getPlayer();
    FireResult result =
        cannons.fire(
            ship, target.relative(), player.getUniqueId(), player.isOp(), clock.getAsLong());
    switch (result) {
      case FIRED -> {
        event.setCancelled(true);
        player.sendMessage("Cannon fired.");
      }
      case UNAUTHORIZED -> {
        event.setCancelled(true);
        player.sendMessage("You cannot fire this cannon.");
      }
      case COOLDOWN -> {
        event.setCancelled(true);
        player.sendMessage("Cannon is reloading.");
      }
      case LAUNCH_FAILED -> {
        event.setCancelled(true);
        logger.warning("Cannon launch failed for ship " + ship.id());
        player.sendMessage("Cannon failed to fire.");
      }
      case NOT_A_CANNON -> {
        // Tagged hull blocks that are not controls retain their normal interaction behavior.
      }
    }
  }
}
