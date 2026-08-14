package dev.jlo.ships.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads and saves {@link Ship} records as {@code ships.json}. Writes go to a
 * temporary sibling file first and are atomically moved into place so an
 * interrupted save cannot corrupt the primary file.
 */
public final class ShipStore {
  private static final String DEFAULT_FILE = "ships.json";
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  private final Path file;

  /** Creates a store backed by {@code dataDirectory/ships.json}. */
  public ShipStore(Path dataDirectory) {
    this.file = dataDirectory.resolve(DEFAULT_FILE);
  }

  /** Loads all persisted ships keyed by identifier in deterministic order. */
  public Map<UUID, Ship> loadAll() throws IOException {
    if (!Files.exists(file)) {
      return Map.of();
    }
    JsonElement root = JsonParser.parseString(Files.readString(file));
    Map<UUID, Ship> ships = new LinkedHashMap<>();
    for (JsonElement element : root.getAsJsonArray()) {
      Ship ship = parseShip(element.getAsJsonObject());
      ships.put(ship.id(), ship);
    }
    return ships;
  }

  /** Saves all ships transactionally. */
  public void saveAll(Map<UUID, Ship> ships) throws IOException {
    JsonArray root = new JsonArray();
    for (Ship ship : ships.values()) {
      root.add(toJson(ship));
    }
    Files.createDirectories(file.getParent());
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    Files.writeString(temporary, GSON.toJson(root));
    try {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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
    return new Ship(id, owner, new ShipOrigin(world, x, y, z), blocks);
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