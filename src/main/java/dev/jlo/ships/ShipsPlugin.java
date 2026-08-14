package dev.jlo.ships;
import dev.jlo.ships.bukkit.BukkitCollisionVolumeManager;
import dev.jlo.ships.bukkit.BukkitScannerWorld;
import dev.jlo.ships.bukkit.BukkitShipRenderer;
import dev.jlo.ships.bukkit.BukkitWorldMutator;
import dev.jlo.ships.command.ShipCommand;
import dev.jlo.ships.command.ShipTabCompleter;
import dev.jlo.ships.config.ShipConfig;
import dev.jlo.ships.config.ShipConfigLoader;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.render.RenderSurface;
import dev.jlo.ships.ship.ShipRuntime;
import dev.jlo.ships.ship.ShipRuntimeImpl;
import dev.jlo.ships.ship.ShipService;
import dev.jlo.ships.ship.ShipServiceImpl;
import dev.jlo.ships.store.ShipStore;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Main plugin entry point. */
public final class ShipsPlugin extends JavaPlugin {
  /** Active ship service. */
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
      RenderSurface surface = RenderSurface.of(world);
      BukkitShipRenderer renderer = new BukkitShipRenderer(surface, shipKey());
      BukkitCollisionVolumeManager collisions =
          new BukkitCollisionVolumeManager(world, new NamespacedKey(this, "collision-owner"));
      ShipRuntime runtime = new ShipRuntimeImpl(renderer, collisions);
      dev.jlo.ships.buoyancy.BuoyancySurface buoyancySurface =
          new dev.jlo.ships.bukkit.BukkitBuoyancySurface(world);
      dev.jlo.ships.buoyancy.BuoyancyEngine engine =
          new dev.jlo.ships.buoyancy.BuoyancyEngine(
              config.gravity(),
              config.waterDensity(),
              config.blockDensity(),
              config.damping(),
              config.physicsTicks());
      dev.jlo.ships.buoyancy.BuoyancyImpl buoyancy =
          new dev.jlo.ships.buoyancy.BuoyancyImpl(
              buoyancySurface, engine, renderer, null, config.maxRise(), config.bobAmplitude());
      service =
          new ShipServiceImpl(
              storeAdapter,
              new BukkitScannerWorld(world, config.maximumBlocks(), forbidden),
              runtime,
              new BukkitWorldMutator(world),
              buoyancy,
              config.buoyancyEnabled(),
              world.getUID());
      service.loadAll();
    } catch (IllegalStateException failure) {
      getLogger().severe("Failed to load ships: " + failure.getMessage());
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    registerCommand(config);
    if (config.buoyancyEnabled()) {
      getServer()
          .getScheduler()
          .runTaskTimer(this, service::tick, config.physicsTicks(), config.physicsTicks());
    }
    getLogger().info("Ships enabled");
  }

  @Override
  public void onDisable() {
    if (service != null) {
      service.removeAllRuntime();
    }
  }

  private void registerCommand(ShipConfig config) {
    PluginCommand command = getCommand("ship");
    if (command == null) {
      getLogger().warning("ship command not registered in plugin.yml");
      return;
    }
    ShipCommand executor =
        new ShipCommand(
            service,
            config,
            new dev.jlo.ships.command.BukkitTargetResolver(config.targetDistance()),
            null);
    command.setTabCompleter(new ShipTabCompleter());
  }

  private NamespacedKey shipKey() {
    return new NamespacedKey(this, "ship-id");
  }

  private static Set<String> normalizeForbidden(Set<String> materials) {
    return materials;
  }

  /** Resolves the primary world used for ship assembly in this version. */
  private static final class WorldBinding {
    /** World used for ship assembly. */
    private final org.bukkit.World world;

    /** Creates a binding to the server's primary world. */
    WorldBinding() {
      this.world = org.bukkit.Bukkit.getWorlds().get(0);
    }

    org.bukkit.World world() {
      return world;
    }
  }

  /** Adapts the JSON store to the ship service contract. */
  private static final class StoreAdapter implements dev.jlo.ships.ship.ShipStoreLike {
    /** Persistent store. */
    private final ShipStore store;

    /**
     * Creates an adapter around the persistent store.
     *
     * @param store persistent store
     */
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
