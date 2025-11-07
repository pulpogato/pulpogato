package io.github.pulpogato.common;

/**
 * An interface representing a type that is defined by Pulpogato.
 */
public interface PulpogatoType {
    /**
     * Converts the object to its code representation.
     *
     * @return A string representing the java code to create the object.
     */
    String toCode();
}
