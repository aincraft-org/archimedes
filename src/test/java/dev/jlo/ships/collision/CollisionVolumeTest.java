package dev.jlo.ships.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract tests for temporary ship collision volumes. */
class CollisionVolumeTest {
  @Test
  void volumeTracksOwnerAndIntegerAnchor() {
    UUID shipId = UUID.randomUUID();
    RecordingVolume volume = new RecordingVolume(shipId);

    assertEquals(shipId, volume.shipId());
    volume.move(4, 5, 6);
    assertEquals("4,5,6", volume.anchor());
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
    public void move(int x, int y, int z) {
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
