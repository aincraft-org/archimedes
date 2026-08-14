package dev.jlo.ships;

import dev.jlo.ships.bukkit.BukkitScannerWorld;
import dev.jlo.ships.bukkit.BukkitShipRenderer;
import dev.jlo.ships.bukkit.BukkitWorldMutator;
import dev.jlo.ships.command.ShipCommand;
import dev.jlo.ships.command.ShipTabCompleter;
import dev.jlo.ships.config.ShipConfig;
import dev.jlo.ships.config.ShipConfigLoader;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.render.RenderSurface;
import dev.jlo.ships.ship.ShipService;
import dev.jlo.ships.ship.ShipServiceImpl;
import dev.jlo.ships.store.ShipStore;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle entry point wiring configuration, services, and commands. */
public final class ShipsPlugin extends JavaPlugin {
  /** The active ship service. */
  private ShipService service;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    ShipConfig config;
    try {
      config = ShipConfigLoader.load(getConfig());
    } catch (IllegalArgumentException failure) {
      getLogger().severe("Invalid configuration: " + failure.getMessage());
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    Set<String> forbidden = normalizeForbidden(config.forbiddenMaterials());
    WorldBinding binding = new WorldBinding();
    ShipStore store = new ShipStore(getDataFolder().toPath());
    StoreAdapter storeAdapter = new StoreAdapter(store);
    org.bukkit.World world = binding.world();
    try {
      service =
          new ShipServiceImpl(
              storeAdapter,
              new BukkitScannerWorld(world, config.maximumBlocks(), forbidden),
              new BukkitShipRenderer(RenderSurface.of(world), shipKey()),
              new BukkitWorldMutator(world),
              new dev.jlo.ships.deck.DeckManager(new dev.jlo.ships.bukkit.BukkitDeckSurface(world)),
              world.getUID());
      service.loadAll();
    } catch (IllegalStateException failure) {
      getLogger().severe("Failed to load ships: " + failure.getMessage());
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    registerCommand();
    getLogger().info("Ships enabled");
  }

  @Override
  public void onDisable() {
    if (service != null) {
      service.removeAllRuntime();
    }
  }

  private void registerCommand() {
    PluginCommand command = getCommand("ship");
    if (command == null) {
      getLogger().warning("ship command not registered in plugin.yml");
      return;
    }
    ShipCommand executor =
        new ShipCommand(
            service,
            loadConfig(),
            new dev.jlo.ships.command.BukkitTargetResolver(loadConfig().targetDistance()));
    command.setExecutor(executor);
    command.setTabCompleter(new ShipTabCompleter());
  }

  private ShipConfig loadConfig() {
    return ShipConfigLoader.load(getConfig());
  }

  private NamespacedKey shipKey() {
    return new NamespacedKey(this, "ship-id");
  }

  private static Set<String> normalizeForbidden(Set<String> materials) {
    return materials;
  }

  /** Resolves the primary world used for ship assembly in this version. */
  private static final class WorldBinding {
    /** The primary world. */
    private final org.bukkit.World world;

    WorldBinding() {
      this.world = org.bukkit.Bukkit.getWorlds().get(0);
    }

    org.bukkit.World world() {
      return world;
    }
  }

  /** Adapts the JSON store to the service contract with runtime wrapping. */
  private static final class StoreAdapter implements dev.jlo.ships.ship.ShipStoreLike {
    /** The underlying JSON store. */
    private final ShipStore store;

    StoreAdapter(ShipStore store) {
      this.store = store;
    }

    @Override
    public Map<UUID, Ship> loadAll() {
      try {
        return store.loadAll();
      } catch (java.io.IOException failure) {
        throw new IllegalStateException("Failed to load ships", failure);
      }
    }

    @Override
    public void saveAll(Map<UUID, Ship> ships) {
      try {
        store.saveAll(ships);
      } catch (java.io.IOException failure) {
        throw new IllegalStateException("Failed to save ships", failure);
      }
    }
  }
}
