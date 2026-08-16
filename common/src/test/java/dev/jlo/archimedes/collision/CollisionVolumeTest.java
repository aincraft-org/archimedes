package dev.jlo.archimedes.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract tests for temporary ship collision volumes. */
class CollisionVolumeTest {
  @Test
  void volumeTracksOwnerAndFractionalAnchor() {
    UUID shipId = UUID.randomUUID();
    RecordingVolume volume = new RecordingVolume(shipId);
    assertEquals(shipId, volume.shipId());
    volume.move(4.5, 5.25, 6.75);
    assertEquals("4.5,5.25,6.75", volume.anchor());
    volume.remove();
    assertTrue(volume.removed);
  }

  private static final class RecordingVolume implements CollisionVolume {
    private final UUID shipId;
    private String anchor;
    private boolean removed;

    RecordingVolume(UUID shipId) {
      this.shipId = shipId;
    }

    @Override
    public UUID shipId() {
      return shipId;
    }

    @Override
    public void move(double x, double y, double z) {
      anchor = x + "," + y + "," + z;
    }

    @Override
    public void remove() {
      removed = true;
    }

    String anchor() {
      return anchor;
    }
  }
}
