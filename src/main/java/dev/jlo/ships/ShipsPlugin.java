package dev.jlo.ships;

import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle entry point for the Ships plugin. */
public final class ShipsPlugin extends JavaPlugin {
  @Override
  public void onEnable() {
    getLogger().info("Ships enabled");
  }

  @Override
  public void onDisable() {
    getLogger().info("Ships disabled");
  }
}
