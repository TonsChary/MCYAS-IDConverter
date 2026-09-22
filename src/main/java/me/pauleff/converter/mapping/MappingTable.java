package me.pauleff.converter.mapping;

import me.pauleff.common.exceptions.MappingException;
import me.pauleff.common.handlers.uuid.MinecraftUuids;
import me.pauleff.converter.ConversionTarget;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and validates the {@code mapping.json} exported from an auth server's player table.
 * <p>
 * This is the only source of UUID mappings for a conversion run; nothing is resolved over the
 * network. The file declares its own {@code direction}, which decides how the returned map is
 * oriented: {@code offline -> third-party} for an onboarding run, {@code third-party -> offline}
 * for an offboarding run.
 * <p>
 * Every entry is checked against the offline UUID algorithm, so a mapping that disagrees with
 * {@code UUID.nameUUIDFromBytes("OfflinePlayer:" + name)} is rejected before any file is touched.
 */
public final class MappingTable
{
    private static final int SUPPORTED_VERSION = 1;
    private static final String OFFLINE_TO_THIRDPARTY = "offline-to-thirdparty";
    private static final String THIRDPARTY_TO_OFFLINE = "thirdparty-to-offline";

    private final ConversionTarget direction;
    private final Map<UUID, UUID> uuidMap;

    private MappingTable(ConversionTarget direction, Map<UUID, UUID> uuidMap)
    {
        this.direction = direction;
        this.uuidMap = uuidMap;
    }

    /**
     * Loads, parses, and fully validates the given mapping file.
     *
     * @param file the {@code mapping.json} path
     * @return the validated mapping table
     * @throws IOException      if the file cannot be read
     * @throws MappingException if the content is malformed, uses an unsupported version, declares
     *                          an unknown direction, has no entries, or contains an entry that
     *                          fails validation
     */
    public static MappingTable load(Path file) throws IOException, MappingException
    {
        JSONObject root = parseRoot(file);
        requireSupportedVersion(root, file);
        ConversionTarget direction = directionOf(root, file);

        JSONArray rawEntries = root.optJSONArray("entries");
        if (rawEntries == null || rawEntries.isEmpty())
        {
            throw new MappingException("Mapping file has no entries: " + file);
        }

        Map<UUID, UUID> uuidMap = new LinkedHashMap<>();
        for (int index = 0; index < rawEntries.length(); index++)
        {
            Entry entry = parseEntry(rawEntries.optJSONObject(index), index, file);
            UUID from = direction == ConversionTarget.ONLINE ? entry.offline() : entry.online();
            UUID to = direction == ConversionTarget.ONLINE ? entry.online() : entry.offline();
            if (uuidMap.putIfAbsent(from, to) != null)
            {
                throw new MappingException(duplicateEntryMessage(direction, from, file));
            }
        }

        return new MappingTable(direction, Collections.unmodifiableMap(uuidMap));
    }

    /**
     * Returns the conversion direction this mapping was exported for.
     *
     * @return {@link ConversionTarget#ONLINE} for an onboarding mapping, otherwise
     * {@link ConversionTarget#OFFLINE}
     */
    public ConversionTarget direction()
    {
        return direction;
    }

    /**
     * Returns the validated {@code from -> to} UUID map, oriented according to {@link #direction()}.
     *
     * @return an unmodifiable map from source UUID to target UUID
     */
    public Map<UUID, UUID> uuidMap()
    {
        return uuidMap;
    }

    private static JSONObject parseRoot(Path file) throws IOException, MappingException
    {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        try
        {
            return new JSONObject(content);
        } catch (JSONException e)
        {
            throw new MappingException("Mapping file is not a valid JSON object: " + file, e);
        }
    }

    private static void requireSupportedVersion(JSONObject root, Path file) throws MappingException
    {
        int version = root.optInt("version", -1);
        if (version != SUPPORTED_VERSION)
        {
            throw new MappingException(
                    "Unsupported mapping version %d in %s (expected %d)".formatted(version, file, SUPPORTED_VERSION));
        }
    }

    private static ConversionTarget directionOf(JSONObject root, Path file) throws MappingException
    {
        String raw = root.optString("direction", "");
        return switch (raw)
        {
            case OFFLINE_TO_THIRDPARTY -> ConversionTarget.ONLINE;
            case THIRDPARTY_TO_OFFLINE -> ConversionTarget.OFFLINE;
            default -> throw new MappingException(
                    "Unknown direction '%s' in %s (expected '%s' or '%s')"
                            .formatted(raw, file, OFFLINE_TO_THIRDPARTY, THIRDPARTY_TO_OFFLINE));
        };
    }

    private static Entry parseEntry(JSONObject raw, int index, Path file) throws MappingException
    {
        if (raw == null)
        {
            throw new MappingException("Entry %d in %s is not a JSON object".formatted(index, file));
        }

        String name = raw.optString("name", "").trim();
        if (name.isEmpty())
        {
            throw new MappingException("Entry %d in %s has no player name".formatted(index, file));
        }

        UUID offline = parseUuid(raw.optString("offline", ""), "offline", name, file);
        UUID online = parseUuid(raw.optString("online", ""), "online", name, file);

        UUID derived = MinecraftUuids.offlineFromName(name);
        if (!derived.equals(offline))
        {
            throw new MappingException(
                    ("Entry for '%s' in %s declares offline UUID %s, but that name derives %s. "
                     + "The offline UUID must be UUID.nameUUIDFromBytes(\"OfflinePlayer:\" + name).")
                            .formatted(name, file, offline, derived));
        }

        return new Entry(name, offline, online);
    }

    private static UUID parseUuid(String value, String field, String name, Path file) throws MappingException
    {
        try
        {
            return MinecraftUuids.parse(value);
        } catch (IllegalArgumentException e)
        {
            throw new MappingException(
                    "Entry for '%s' in %s has an invalid %s UUID: '%s'".formatted(name, file, field, value), e);
        }
    }

    private static String duplicateEntryMessage(ConversionTarget direction, UUID duplicate, Path file)
    {
        if (direction == ConversionTarget.ONLINE)
        {
            return "Duplicate offline UUID %s in %s. Each player name must appear at most once."
                    .formatted(duplicate, file);
        }
        return ("Duplicate third-party UUID %s in %s. A '%s' mapping must contain exactly one entry per "
                + "account, using the current name: historical names cannot be resolved to a single "
                + "offline UUID.").formatted(duplicate, file, THIRDPARTY_TO_OFFLINE);
    }

    /**
     * A single validated player entry.
     *
     * @param name    the player name the offline UUID was derived from
     * @param offline the offline (v3) UUID
     * @param online  the third-party (v4) UUID
     */
    private record Entry(String name, UUID offline, UUID online)
    {
    }
}
