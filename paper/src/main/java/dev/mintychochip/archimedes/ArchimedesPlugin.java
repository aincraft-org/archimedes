package dev.mintychochip.archimedes;

import dev.mintychochip.archimedes.bukkit.BukkitCollisionVolumeManager;
import dev.mintychochip.archimedes.bukkit.BukkitScannerWorld;
import dev.mintychochip.archimedes.bukkit.BukkitShipEntityCarrier;
import dev.mintychochip.archimedes.bukkit.BukkitShipRenderer;
import dev.mintychochip.archimedes.bukkit.BukkitShipRiderTracker;
import dev.mintychochip.archimedes.bukkit.BukkitWorldMutator;
import dev.mintychochip.archimedes.command.ShipCommand;
import dev.mintychochip.archimedes.command.ShipTabCompleter;
import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.config.ShipConfigLoader;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.phys.ShipPhysics;
import dev.mintychochip.archimedes.phys.ShipPhysicsImpl;
import dev.mintychochip.archimedes.phys.bukkit.BukkitFluidField;
import dev.mintychochip.archimedes.phys.bukkit.BukkitMaterialKeyResolver;
import dev.mintychochip.archimedes.phys.bukkit.BukkitPhysicsWorld;
import dev.mintychochip.archimedes.render.RenderSurface;
import dev.mintychochip.archimedes.ship.ShipRuntime;
import dev.mintychochip.archimedes.ship.ShipRuntimeImpl;
import dev.mintychochip.archimedes.ship.ShipService;
import dev.mintychochip.archimedes.ship.ShipServiceImpl;
import dev.mintychochip.archimedes.store.ShipStore;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.FlowField;
import dev.mintychochip.phys.PhysicsEngine;
import dev.mintychochip.phys.World;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Main plugin entry point. */
public final class ArchimedesPlugin extends JavaPlugin {
  /** Active ship service. */
  private ShipService service;

  /**
   * Enables the plugin by loading configuration, constructing the Bukkit-backed ship runtime, and
   * starting the ship service. Invalid configuration disables the plugin; other startup failures
   * are logged and also disable it.
   */
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
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
      NamespacedKey shipKey = shipKey();
      NamespacedKey collisionOwnerKey = new NamespacedKey(this, "collision-owner");
      Supplier<Collection<Ship>> allShips = () -> service.all();
      BukkitShipRiderTracker tracker =
          new BukkitShipRiderTracker(world, allShips, collisionOwnerKey, shipKey);
      RenderSurface surface = RenderSurface.of(world);
      BukkitShipRenderer renderer = new BukkitShipRenderer(surface, shipKey);
      BukkitCollisionVolumeManager collisions =
          new BukkitCollisionVolumeManager(world, collisionOwnerKey);
      BukkitShipEntityCarrier carrier =
          new BukkitShipEntityCarrier(world, collisionOwnerKey, shipKey, tracker);
      ShipRuntime runtime = new ShipRuntimeImpl(renderer, collisions, carrier);
      BukkitFluidField fluidField = new BukkitFluidField(world, config.waterDensity());
      BukkitMaterialKeyResolver materialResolver = new BukkitMaterialKeyResolver();
      World physicsWorld = new BukkitPhysicsWorld(world, config, fluidField);
      ShipPhysics shipPhysics =
          new ShipPhysicsImpl(
              new PhysicsEngine(),
              physicsWorld,
              config,
              materialResolver,
              runtime,
              ship -> tracker.riders(ship).size(),
              DensityField.uniform(1.2),
              FlowField.uniform(new org.joml.Vector3d(0, 0, 8)));
      service =
          new ShipServiceImpl(
              storeAdapter,
              new BukkitScannerWorld(world, config.maximumBlocks(), forbidden),
              runtime,
              new BukkitWorldMutator(world),
              shipPhysics,
              config.buoyancyEnabled(),
              config.worldEnabled(world.getUID()),
              world.getUID());
      try {
        registerAfterLoad(
            () -> service.loadAll(),
            () -> getServer().getPluginManager().registerEvents(tracker, this));
      } finally {
        if (service == null) {
          tracker.clear();
        }
      }
    } catch (RuntimeException failure) {
      CleanupCoordinator.handleLoadFailure(
          failure,
          message -> getLogger().severe(message),
          () -> getServer().getPluginManager().disablePlugin(this));
      return;
    }
    registerCommand(config);
    if (config.buoyancyEnabled()) {
      getServer()
          .getScheduler()
          .runTaskTimer(this, service::tick, config.physicsTicks(), config.physicsTicks());
    }
    getLogger().info("Archimedes enabled");
  }

  /**
   * Stops the plugin's ship service and removes runtime entities when the plugin is disabled.
   *
   * <p>Cleanup is best-effort: registered runtime state and tagged Bukkit entities are attempted
   * independently so a failure in one path does not prevent the other.
   */
  @Override
  public void onDisable() {
    if (service == null) {
      return;
    }
    CleanupCoordinator.run(
        () -> service.removeAllRuntime(),
        () -> ((ShipServiceImpl) service).runtime().removeAllTagged(),
        message -> getLogger().severe(message));
  }

  /**
   * Returns the service initialized by {@link #onEnable()}, or {@code null} before successful
   * startup.
   *
   * @return the active ship service
   */
  public ShipService shipService() {
    return service;
  }

  /**
   * Loads persisted ships before registering listeners that observe ship-related entities.
   *
   * @param load action that loads the persisted ship state
   * @param registration action that registers event listeners after loading
   */
  static void registerAfterLoad(Runnable load, Runnable registration) {
    load.run();
    registration.run();
  }

  static final class CleanupCoordinator {
    /**
     * Handles a load failure by logging it and disabling the plugin.
     *
     * @param failure failure raised while loading
     * @param logger failure logger
     * @param disable action that disables the plugin
     */
    static void handleLoadFailure(
        RuntimeException failure, java.util.function.Consumer<String> logger, Runnable disable) {
      logger.accept("Failed to load Archimedes: " + failure.getMessage());
      disable.run();
    }

    private CleanupCoordinator() {}

    /**
     * Attempts both cleanup actions even when an unnormalized runtime failure escapes an adapter.
     *
     * @param removeRegistered registered runtime cleanup
     * @param removeTagged tagged entity cleanup
     * @param log failure logger
     */
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    static void run(
        Runnable removeRegistered, Runnable removeTagged, java.util.function.Consumer<String> log) {
      try {
        removeRegistered.run();
      } catch (RuntimeException failure) {
        log.accept("Failed to remove registered ship runtime: " + failure.getMessage());
      }
      try {
        removeTagged.run();
      } catch (RuntimeException failure) {
        log.accept("Failed to remove tagged ship runtime: " + failure.getMessage());
      }
    }
  }

  /**
   * Registers the {@code /ship} command executor and tab completer if the command is declared in
   * the plugin configuration.
   *
   * @param config loaded ship configuration
   */
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
            new dev.mintychochip.archimedes.command.BukkitTargetResolver(config.targetDistance()));
    command.setExecutor(executor);
    command.setTabCompleter(new ShipTabCompleter());
  }

  /** Returns the namespaced key used to tag Bukkit entities as part of a ship. */
  private NamespacedKey shipKey() {
    return new NamespacedKey(this, "ship-id");
  }

  /**
   * Returns the forbidden material set without modification.
   *
   * @param materials forbidden materials from configuration
   * @return the same set of materials
   */
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

    /** Returns the primary Bukkit world used for ship assembly. */
    org.bukkit.World world() {
      return world;
    }
  }

  /** Adapts the JSON store to the ship service contract. */
  private static final class StoreAdapter
      implements dev.mintychochip.archimedes.ship.ShipStoreLike {
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

    /**
     * Loads all persisted ships, converting IO failures into a runtime exception.
     *
     * @return the loaded ships by id
     */
    @Override
    public Map<UUID, Ship> loadAll() {
      try {
        return store.loadAll();
      } catch (java.io.IOException failure) {
        throw new dev.mintychochip.archimedes.ship.ShipRuntimeException(failure);
      }
    }

    /**
     * Saves all ships through the persistent store, converting IO failures into a runtime
     * exception.
     *
     * @param ships ships to save
     */
    @Override
    public void saveAll(Map<UUID, Ship> ships) {
      try {
        store.saveAll(ships);
      } catch (java.io.IOException failure) {
        throw new dev.mintychochip.archimedes.ship.ShipRuntimeException(failure);
      }
    }
  }
}
