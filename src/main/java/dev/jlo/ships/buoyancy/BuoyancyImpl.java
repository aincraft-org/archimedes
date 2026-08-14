package dev.jlo.ships.buoyancy;

import dev.jlo.ships.deck.DeckManager;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipPose;
import dev.jlo.ships.ship.ShipRendererLike;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Default buoyancy: rises on assembly, integrates bobbing, applies moves all-or-nothing. */
public final class BuoyancyImpl implements Buoyancy {
  /** World surface. */
  private final BuoyancySurface surface;

  /** Force integrator. */
  private final BuoyancyEngine engine;

  /** Renderer for repositioning displays. */
  private final ShipRendererLike renderer;

  /** Deck manager for support re-deployment. */
  private final DeckManager deck;

  /** Maximum rise from build site. */
  private final double maxRise;

  /** Maximum vertical bob amplitude. */
  private final double bobAmplitude;

  /** Per-ship vertical velocity. */
  private final Map<UUID, Double> velocities = new HashMap<>();

  /** Per-ship equilibrium pose y. */
  private final Map<UUID, Double> equilibria = new HashMap<>();

  /**
   * Creates the buoyancy implementation.
   *
   * @param surface the world surface
   * @param engine the force integrator
   * @param renderer the renderer
   * @param deck the deck manager
   * @param maxRise the maximum rise from build site
   * @param bobAmplitude the maximum bob amplitude
   */
  public BuoyancyImpl(
      BuoyancySurface surface,
      BuoyancyEngine engine,
      ShipRendererLike renderer,
      DeckManager deck,
      double maxRise,
      double bobAmplitude) {
    this.surface = surface;
    this.engine = engine;
    this.renderer = renderer;
    this.deck = deck;
    this.maxRise = maxRise;
    this.bobAmplitude = bobAmplitude;
  }

  @Override
  public boolean rise(Ship ship) {
    if (!ship.buoyancyEnabled()) {
      return true;
    }
    double target = BuoyancyResolver.equilibriumY(ship, surface);
    double oldY = ship.pose().y();
    if (!pathClear(ship, oldY, target)) {
      return false;
    }
    velocities.put(ship.id(), 0.0);
    equilibria.put(ship.id(), target);
    return moveTo(ship, oldY, target);
  }

  @Override
  public void tick(Ship ship) {
    if (!ship.buoyancyEnabled()) {
      return;
    }
    double velocity = velocities.getOrDefault(ship.id(), 0.0);
    BuoyancyEngine.Step step = engine.step(ship, velocity, surface);
    double equilibrium = equilibria.getOrDefault(ship.id(), ship.pose().y());
    double clamped = clamp(ship, step.y(), equilibrium);
    if (Math.abs(clamped - ship.pose().y()) < 0.001) {
      velocities.put(ship.id(), 0.0);
      return;
    }
    if (!pathClear(ship, ship.pose().y(), clamped)) {
      velocities.put(ship.id(), 0.0);
      return;
    }
    velocities.put(ship.id(), step.velocity());
    moveTo(ship, ship.pose().y(), clamped);
  }

  @Override
  public boolean sink(Ship ship, int blocks) {
    if (!ship.buoyancyEnabled()) {
      return false;
    }
    double target = Math.max(0, ship.pose().y() - blocks);
    if (!pathClear(ship, ship.pose().y(), target)) {
      return false;
    }
    return moveTo(ship, ship.pose().y(), target);
  }

  @Override
  public void clear(Ship ship) {
    velocities.remove(ship.id());
    equilibria.remove(ship.id());
  }

  private double clamp(Ship ship, double y, double equilibrium) {
    double lower = Math.max(0, equilibrium - bobAmplitude);
    double upper = Math.min(maxRise, equilibrium + bobAmplitude);
    return Math.max(lower, Math.min(upper, y));
  }

  private boolean moveTo(Ship ship, double oldY, double newY) {
    int oldAnchor = (int) Math.floor(oldY);
    int newAnchor = (int) Math.floor(newY);
    if (oldAnchor != newAnchor) {
      deck.remove(ship);
    }
    ship.setPose(new ShipPose(newY));
    renderer.reposition(ship, oldY, newY);
    if (oldAnchor != newAnchor) {
      if (!deck.deploy(ship)) {
        ship.setPose(new ShipPose(oldY));
        renderer.reposition(ship, newY, oldY);
        deck.deploy(ship);
        return false;
      }
    }
    return true;
  }

  private boolean pathClear(Ship ship, double fromY, double toY) {
    int from = (int) Math.floor(Math.min(fromY, toY));
    int to = (int) Math.floor(Math.max(fromY, toY));
    for (int y = from; y <= to; y++) {
      for (var block : ship.blocks()) {
        int ax = ship.origin().x() + block.pos().x();
        int ay = ship.origin().y() + y + block.pos().y();
        int az = ship.origin().z() + block.pos().z();
        if (!surface.isClear(ax, ay, az)) {
          return false;
        }
      }
    }
    return true;
  }
}
