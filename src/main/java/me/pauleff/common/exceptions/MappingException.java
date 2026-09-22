package me.pauleff.common.exceptions;

/**
 * Indicates that a mapping file is missing, malformed, or inconsistent with the requested conversion.
 */
public class MappingException extends Exception
{
    /**
     * Creates an exception with the given detail message.
     *
     * @param message the explanation shown to the user
     */
    public MappingException(String message)
    {
        super(message);
    }

    /**
     * Creates an exception with the given detail message and cause.
     *
     * @param message the explanation shown to the user
     * @param cause   the underlying failure
     */
    public MappingException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
