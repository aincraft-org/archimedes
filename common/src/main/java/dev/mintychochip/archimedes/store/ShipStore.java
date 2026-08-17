package dev.mintychochip.archimedes.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads and saves {@link Ship} records as {@code archimedes.json}. Writes go to a temporary sibling
 * file first and are atomically moved into place so an interrupted save cannot corrupt the primary
 * file. A leftover {@code ships.json} is read when the new file is absent.
 */
public final class ShipStore {
  /** Primary persistence file name. */
  private static final String DEFAULT_FILE = "archimedes.json";

  /** Previous product filename still accepted on load. */
  private static final String LEGACY_FILE = "ships.json";

  /** JSON serializer with deterministic formatting. */
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  /** The primary persistence file. */
  private final Path file;

  /** Legacy persistence file from the Ships product name. */
  private final Path legacyFile;

  /**
   * Creates a store backed by {@code dataDirectory/archimedes.json}.
   *
   * @param dataDirectory the plugin data directory
   */
  public ShipStore(Path dataDirectory) {
    this.file = dataDirectory.resolve(DEFAULT_FILE);
    this.legacyFile = dataDirectory.resolve(LEGACY_FILE);
  }

  /**
   * Loads all persisted ships keyed by identifier in deterministic order.
   *
   * @return the persisted ships keyed by identifier
   * @throws IOException when the file cannot be read
   */
  public Map<UUID, Ship> loadAll() throws IOException {
    Path source = Files.exists(file) ? file : legacyFile;
    if (!Files.exists(source)) {
      return Map.of();
    }
    JsonElement root = JsonParser.parseString(Files.readString(source));
    Map<UUID, Ship> ships = new LinkedHashMap<>();
    for (JsonElement element : root.getAsJsonArray()) {
      Ship ship = parseShip(element.getAsJsonObject());
      ships.put(ship.id(), ship);
    }
    return ships;
  }

  /**
   * Saves all ships transactionally.
   *
   * @param ships the ships to save
   * @throws IOException when the file cannot be written
   */
  public void saveAll(Map<UUID, Ship> ships) throws IOException {
    JsonArray root = new JsonArray();
    for (Ship ship : ships.values()) {
      root.add(toJson(ship));
    }
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    Files.writeString(temporary, GSON.toJson(root));
    try {
      Files.move(
          temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static JsonObject toJson(Ship ship) {
    JsonObject object = new JsonObject();
    object.addProperty("id", ship.id().toString());
    object.addProperty("owner", ship.ownerId().toString());
    JsonObject origin = new JsonObject();
    origin.addProperty("world", ship.origin().worldId().toString());
    origin.addProperty("x", ship.origin().x());
    origin.addProperty("y", ship.origin().y());
    origin.addProperty("z", ship.origin().z());
    object.add("origin", origin);
    if (ship.pose().x() != 0 || ship.pose().y() != 0 || ship.pose().z() != 0) {
      JsonObject pose = new JsonObject();
      if (ship.pose().x() != 0) {
        pose.addProperty("x", ship.pose().x());
      }
      pose.addProperty("y", ship.pose().y());
      if (ship.pose().z() != 0) {
        pose.addProperty("z", ship.pose().z());
      }
      object.add("pose", pose);
    }
    if (!ship.buoyancyEnabled()) {
      object.addProperty("buoyancy", false);
    }
    JsonArray blocks = new JsonArray();
    for (ShipBlock block : ship.blocks()) {
      JsonObject entry = new JsonObject();
      JsonObject pos = new JsonObject();
      pos.addProperty("x", block.pos().x());
      pos.addProperty("y", block.pos().y());
      pos.addProperty("z", block.pos().z());
      entry.add("pos", pos);
      entry.addProperty("data", block.blockData());
      blocks.add(entry);
    }
    object.add("blocks", blocks);
    return object;
  }

  private static Ship parseShip(JsonObject object) {
    UUID id = UUID.fromString(requireString(object, "id"));
    UUID owner = UUID.fromString(requireString(object, "owner"));
    JsonObject originJson = requireObject(object, "origin");
    UUID world = UUID.fromString(requireString(originJson, "world"));
    int x = originJson.get("x").getAsInt();
    int y = originJson.get("y").getAsInt();
    int z = originJson.get("z").getAsInt();
    List<ShipBlock> blocks = new ArrayList<>();
    for (JsonElement entry : requireArray(object, "blocks")) {
      JsonObject blockJson = entry.getAsJsonObject();
      JsonObject posJson = requireObject(blockJson, "pos");
      blocks.add(
          new ShipBlock(
              new BlockPos(
                  posJson.get("x").getAsInt(),
                  posJson.get("y").getAsInt(),
                  posJson.get("z").getAsInt()),
              requireString(blockJson, "data")));
    }
    double poseX = 0;
    double poseY = 0;
    double poseZ = 0;
    if (object.has("pose")) {
      JsonObject pose = object.getAsJsonObject("pose");
      if (pose.has("x")) {
        poseX = pose.get("x").getAsDouble();
      }
      if (pose.has("y")) {
        poseY = pose.get("y").getAsDouble();
      }
      if (pose.has("z")) {
        poseZ = pose.get("z").getAsDouble();
      }
    }
    boolean buoyancyEnabled = !object.has("buoyancy") || object.get("buoyancy").getAsBoolean();
    return new Ship(
        id,
        owner,
        new ShipOrigin(world, x, y, z),
        blocks,
        new ShipPose(poseX, poseY, poseZ),
        buoyancyEnabled);
  }

  private static String requireString(JsonObject object, String key) {
    if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
      throw new JsonSyntaxException("missing string field " + key);
    }
    return object.get(key).getAsString();
  }

  private static JsonObject requireObject(JsonObject object, String key) {
    if (!object.has(key) || !object.get(key).isJsonObject()) {
      throw new JsonSyntaxException("missing object field " + key);
    }
    return object.get(key).getAsJsonObject();
  }

  private static JsonArray requireArray(JsonObject object, String key) {
    if (!object.has(key) || !object.get(key).isJsonArray()) {
      throw new JsonSyntaxException("missing array field " + key);
    }
    return object.get(key).getAsJsonArray();
  }
}
