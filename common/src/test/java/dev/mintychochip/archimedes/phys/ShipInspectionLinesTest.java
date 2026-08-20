package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShipInspectionLinesTest {
  @Test
  void colorCodesForceVectorComponents() {
    ShipInspection report =
        new ShipInspection(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            1,
            1,
            0,
            10,
            true,
            true,
            0,
            0,
            0,
            0,
            0,
            0,
            4.0,
            -1.0,
            8.0,
            0,
            0,
            0,
            List.of(new ShipInspection.ForceLine("Sail +Z 4m2", 1.25, -2.5, 3.75, 0, 0, 0)),
            1.25,
            -2.5,
            3.75);

    List<String> lines = ShipInspectionLines.lines(report);
    String force = lines.stream().filter(line -> line.contains("Sail +Z")).findFirst().orElse("");
    assertTrue(force.contains("\u00A7c1.25\u00A7r"), force);
    assertTrue(force.contains("\u00A7a-2.50\u00A7r"), force);
    assertTrue(force.contains("\u00A7b3.75\u00A7r"), force);
    assertTrue(force.contains("\u00A7e"), force);
    String wind = lines.stream().filter(line -> line.contains("wind=")).findFirst().orElse("");
    assertTrue(wind.contains("\u00A7c4.00\u00A7r"), wind);
    assertTrue(wind.contains("\u00A7a-1.00\u00A7r"), wind);
    assertTrue(wind.contains("\u00A7b8.00\u00A7r"), wind);
    String net = lines.stream().filter(line -> line.contains("net ")).findFirst().orElse("");
    assertTrue(net.contains("\u00A7c1.25\u00A7r"), net);
    assertTrue(net.contains("\u00A7a-2.50\u00A7r"), net);
    assertTrue(net.contains("\u00A7b3.75\u00A7r"), net);
  }
}
