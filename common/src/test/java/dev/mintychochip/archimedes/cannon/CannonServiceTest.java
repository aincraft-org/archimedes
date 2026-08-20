package dev.mintychochip.archimedes.cannon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mintychochip.archimedes.cannon.CannonLauncher.Shot;
import dev.mintychochip.archimedes.cannon.CannonService.FireResult;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior tests for authorization, geometry, and cooldown. */
class CannonServiceTest {
  private static final BlockPos CONTROL = new BlockPos(1, 0, 0);

  @Test
  void ownerFiresFromCurrentTransformedMuzzle() {
    RecordingLauncher launcher = new RecordingLauncher();
    CannonService service = new CannonService(launcher);
    Vehicle ship = cannonShip();
    ship.setPose(new ShipPose(2.5, 3.0, -1.0));

    assertEquals(FireResult.FIRED, service.fire(ship, CONTROL, ship.ownerId(), false, 1000));
    assertEquals(ship.id(), launcher.last.shipId());
    assertEquals(13.0, launcher.last.x());
    assertEquals(67.5, launcher.last.y());
    assertEquals(18.75, launcher.last.z());
    assertEquals(-1.0, launcher.last.dz());
  }

  @Test
  void enforcesAuthorizationAndPerCannonCooldown() {
    RecordingLauncher launcher = new RecordingLauncher();
    CannonService service = new CannonService(launcher);
    Vehicle ship = cannonShip();
    UUID stranger = UUID.randomUUID();

    assertEquals(FireResult.UNAUTHORIZED, service.fire(ship, CONTROL, stranger, false, 1000));
    assertEquals(FireResult.FIRED, service.fire(ship, CONTROL, stranger, true, 1000));
    assertEquals(FireResult.COOLDOWN, service.fire(ship, CONTROL, stranger, true, 2999));
    assertEquals(FireResult.FIRED, service.fire(ship, CONTROL, stranger, true, 3000));
  }

  @Test
  void failedLaunchDoesNotStartCooldownAndClearDropsState() {
    RecordingLauncher launcher = new RecordingLauncher();
    launcher.fail = true;
    CannonService service = new CannonService(launcher);
    Vehicle ship = cannonShip();

    assertEquals(
        FireResult.LAUNCH_FAILED, service.fire(ship, CONTROL, ship.ownerId(), false, 1000));
    launcher.fail = false;
    assertEquals(FireResult.FIRED, service.fire(ship, CONTROL, ship.ownerId(), false, 1001));
    service.clear(ship.id());
    assertEquals(FireResult.FIRED, service.fire(ship, CONTROL, ship.ownerId(), false, 1002));
  }

  private static Vehicle cannonShip() {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 10, 64, 20),
        List.of(
            new ShipBlock(
                new BlockPos(0, 0, 0), "minecraft:dispenser[facing=north,triggered=false]"),
            new ShipBlock(CONTROL, "minecraft:stone_button[face=wall,facing=east,powered=false]")));
  }

  private static final class RecordingLauncher implements CannonLauncher {
    private Shot last;
    private boolean fail;

    @Override
    public void launch(Shot shot) {
      if (fail) {
        throw new IllegalStateException("launch failed");
      }
      last = shot;
    }
  }
}
