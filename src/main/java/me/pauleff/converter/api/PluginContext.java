package me.pauleff.converter.api;

import me.pauleff.common.argparse.ParsedArguments;
import me.pauleff.common.exceptions.MappingException;
import me.pauleff.common.exceptions.PathNotValidException;
import me.pauleff.common.handlers.files.ServerPropertiesFile;
import me.pauleff.converter.ConversionTarget;
import me.pauleff.converter.SaveFileFormat;
import me.pauleff.converter.ServerType;
import me.pauleff.converter.WorldFolderStructure;
import me.pauleff.converter.mapping.MappingTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Holds shared state for a conversion run and is passed to each {@link MOOCPlugin}.
 * <p>
 * Created from {@link ParsedArguments}, it resolves the server and world folders and
 * accumulates detection results (server type, folder structure, save format) plus
 * UUID remappings produced during conversion.
 */
public final class PluginContext
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginContext.class);

    private final Path serverFolder;
    private final Path worldFolder;
    private final ConversionTarget conversionTarget;
    private final Map<UUID, UUID> uuidMap;
    private final ParsedArguments parsedArguments;
    private ServerType serverType;
    private WorldFolderStructure worldFolderStructure;
    private SaveFileFormat saveFileFormat;

    /**
     * Creates a context with the given folders, conversion target, parsed arguments, and UUID map.
     * <p>
     * Detection fields start unset.
     *
     * @param serverFolder     the absolute, normalized server root folder
     * @param worldFolder      the world folder resolved from {@code server.properties}
     * @param conversionTarget whether to convert toward online or offline mode
     * @param parsedArguments  the CLI arguments for this run
     * @param uuidMap          the source-to-target UUID mappings loaded from the mapping file
     */
    private PluginContext(
            Path serverFolder,
            Path worldFolder,
            ConversionTarget conversionTarget,
            ParsedArguments parsedArguments,
            Map<UUID, UUID> uuidMap)
    {
        this.serverFolder = Objects.requireNonNull(serverFolder, "Server folder path can't be null.");
        this.worldFolder = Objects.requireNonNull(worldFolder, "World folder path can't be null.");
        this.conversionTarget = Objects.requireNonNull(conversionTarget, "Target to convert to must be set.");
        this.parsedArguments = Objects.requireNonNull(parsedArguments, "Parsed arguments can't be null.");
        this.uuidMap = Map.copyOf(uuidMap);
    }

    /**
     * Builds a {@link PluginContext} from successfully parsed CLI arguments.
     * <p>
     * Resolves the server folder (defaulting to the current directory), requires
     * {@code server.properties}, derives the world folder from {@code level-name},
     * sets the conversion target from the online/offline flags (offline when absent),
     * and loads the mapping file that drives a conversion run.
     *
     * @param parsedArgs the parsed CLI arguments
     * @return a new context ready for plugin execution
     * @throws PathNotValidException if the server folder, {@code server.properties}, or world folder is missing
     * @throws MappingException      if a conversion run has no usable mapping file, or the mapping
     *                               was exported for the opposite direction
     * @throws NullPointerException  if {@code parsedArgs} is {@code null}
     */
    public static PluginContext from(ParsedArguments parsedArgs) throws PathNotValidException, MappingException
    {
        Objects.requireNonNull(parsedArgs, "Parsed arguments can't be null.");

        Path serverFolder = parsedArgs.serverPath()
                .orElse(Path.of("."))
                .toAbsolutePath()
                .normalize();

        if (!Files.exists(serverFolder))
        {
            throw new PathNotValidException("Server folder not found", serverFolder);
        }
        LOGGER.info("Server folder set to: {}", serverFolder);

        Path serverProperties = serverFolder.resolve("server.properties");
        if (!Files.exists(serverProperties))
        {
            throw new PathNotValidException(
                    "Could not find server.properties",
                    serverProperties.toAbsolutePath().normalize());
        }

        String worldName = ServerPropertiesFile.worldName(serverProperties);
        Path worldFolder = serverFolder.resolve(worldName);
        if (!Files.exists(worldFolder))
        {
            throw new PathNotValidException(worldFolder.toAbsolutePath().normalize());
        }

        ConversionTarget conversionTarget = parsedArgs.toOnlineMode()
                .map(online -> online ? ConversionTarget.ONLINE : ConversionTarget.OFFLINE)
                .orElse(ConversionTarget.OFFLINE);

        return new PluginContext(serverFolder, worldFolder, conversionTarget, parsedArgs,
                loadUuidMap(parsedArgs, conversionTarget));
    }

    /**
     * Loads the mapping table that drives a conversion run and checks its declared direction.
     * <p>
     * Returns an empty map for runs that are not conversions, such as {@code -copy} or
     * {@code -properties} only.
     *
     * @param parsedArgs       the parsed CLI arguments
     * @param conversionTarget the direction this run converts toward
     * @return the source-to-target UUID map, or an empty map when no conversion was requested
     * @throws MappingException if no mapping file was given, it cannot be read, it is invalid,
     *                          or it was exported for the opposite direction
     */
    private static Map<UUID, UUID> loadUuidMap(ParsedArguments parsedArgs, ConversionTarget conversionTarget)
            throws MappingException
    {
        if (!parsedArgs.isConversionOperation())
        {
            return Map.of();
        }

        Path mappingFile = parsedArgs.uuidMapPath().orElseThrow(() -> new MappingException(
                "A conversion requires -uuidMap pointing at the mapping.json exported by your auth server."));

        MappingTable table;
        try
        {
            table = MappingTable.load(mappingFile);
        } catch (IOException e)
        {
            throw new MappingException("Could not read the mapping file " + mappingFile, e);
        }

        if (table.direction() != conversionTarget)
        {
            throw new MappingException(
                    ("mapping.json was exported for the %s direction, but this run uses %s. "
                     + "Regenerate the mapping for this direction, or pass the matching flag.")
                            .formatted(flagOf(table.direction()), flagOf(conversionTarget)));
        }

        LOGGER.info("Loaded {} UUID mapping(s) from {}", table.uuidMap().size(), mappingFile);
        return table.uuidMap();
    }

    /**
     * Returns the command-line flag that selects the given conversion direction.
     *
     * @param target the conversion direction
     * @return {@code -online} or {@code -offline}
     */
    private static String flagOf(ConversionTarget target)
    {
        return target == ConversionTarget.ONLINE ? "-online" : "-offline";
    }

    /**
     * Returns the remapped UUID previously stored for the given original UUID.
     *
     * @param from the original UUID to look up
     * @return the remapped UUID, or {@code null} if no mapping exists
     * @throws NullPointerException if {@code from} is {@code null}
     */
    public UUID getTargetUuid(UUID from)
    {
        return uuidMap.get(Objects.requireNonNull(from, "Original UUID to put into map can't be null."));
    }

    /**
     * Returns the absolute, normalized path to the server root folder.
     *
     * @return the server folder path
     */
    public Path serverFolder()
    {
        return serverFolder;
    }

    /**
     * Returns the path to the world folder resolved from {@code server.properties}.
     *
     * @return the world folder path
     */
    public Path worldFolder()
    {
        return worldFolder;
    }

    /**
     * Returns whether this run converts toward online or offline mode.
     *
     * @return the conversion target
     */
    public ConversionTarget conversionTarget()
    {
        return conversionTarget;
    }

    /**
     * Returns the detected server type, if set by a detection plugin.
     *
     * @return the server type, or {@code null} if not yet detected
     */
    public ServerType serverType()
    {
        return serverType;
    }

    /**
     * Returns the detected world folder structure, if set by a detection plugin.
     *
     * @return the world folder structure, or {@code null} if not yet detected
     */
    public WorldFolderStructure worldFolderStructure()
    {
        return worldFolderStructure;
    }

    /**
     * Returns the detected save file format, if set by a detection plugin.
     *
     * @return the save file format, or {@code null} if not yet detected
     */
    public SaveFileFormat saveFileFormat()
    {
        return saveFileFormat;
    }

    /**
     * Returns the original-to-remapped player UUID map loaded from the mapping file.
     *
     * @return an unmodifiable UUID remapping map; never {@code null}
     */
    public Map<UUID, UUID> uuidMap()
    {
        return uuidMap;
    }

    /**
     * Sets the detected server type for this conversion run.
     *
     * @param serverType the detected {@link ServerType}
     */
    public void setServerType(ServerType serverType)
    {
        this.serverType = serverType;
    }

    /**
     * Sets the detected world folder structure for this conversion run.
     *
     * @param worldFolderStructure the detected {@link WorldFolderStructure}
     */
    public void setWorldFolderStructure(WorldFolderStructure worldFolderStructure)
    {
        this.worldFolderStructure = worldFolderStructure;
    }

    /**
     * Sets the detected save file format for this conversion run.
     *
     * @param saveFileFormat the detected {@link SaveFileFormat}
     */
    public void setSaveFileFormat(SaveFileFormat saveFileFormat)
    {
        this.saveFileFormat = saveFileFormat;
    }

    /**
     * Returns the parsed CLI arguments for this run.
     *
     * @return the {@link ParsedArguments} used to build this context
     */
    public ParsedArguments parsedArguments()
    {
        return parsedArguments;
    }

    /**
     * Indicates whether an online/offline conversion was requested on the command line.
     *
     * @return {@code true} if a conversion operation was requested; {@code false} otherwise
     */
    public boolean isConversionOperation()
    {
        return parsedArguments.isConversionOperation();
    }

    /**
     * Returns a string representation of this context for debugging.
     *
     * @return a string including folders, conversion target, detection fields, and UUID map
     */
    @Override
    public String toString()
    {
        return "PluginContext{" +
                "serverFolder=" + serverFolder +
                ", worldFolder=" + worldFolder +
                ", conversionTarget=" + conversionTarget.name() +
                ", serverType=" + serverType.name() +
                ", worldFolderStructure=" + worldFolderStructure.name() +
                ", saveFileFormat=" + saveFileFormat.name() +
                ", uuidMap=" + uuidMap +
                '}';
    }
}
