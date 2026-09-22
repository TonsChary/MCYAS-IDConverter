package me.pauleff.common;

/**
 * Process exit codes used by the CLI.
 * <p>
 * These are part of the tool's contract with wrapper scripts, which need to tell a successful
 * conversion apart from a run that declined or refused to change anything.
 * <p>
 * Codes {@code 2} (preflight gate refused) and {@code 4} (artifact or mapping integrity check
 * failed) are reserved for the wrapper that drives this tool.
 */
public final class ExitCode
{
    /**
     * The run completed and any requested conversion was applied.
     */
    public static final int SUCCESS = 0;

    /**
     * The command line was missing required options or contained unusable values.
     */
    public static final int USAGE = 1;

    /**
     * The mapping file was absent, malformed, or exported for the opposite direction.
     */
    public static final int INVALID_MAPPING = 3;

    /**
     * The world uses a pre-1.7.6 save format, which predates Minecraft UUIDs, so there is
     * nothing to convert.
     */
    public static final int NOTHING_TO_CONVERT = 5;

    /**
     * A conversion was requested but the mapping resolved no usable profiles.
     */
    public static final int NO_PROFILES = 6;

    private ExitCode()
    {
    }
}
