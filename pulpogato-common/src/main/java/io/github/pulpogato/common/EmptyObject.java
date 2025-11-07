package io.github.pulpogato.common;

/**
 * <strong>Empty Object</strong>
 * <p>
 * An object without any properties.
 */
public class EmptyObject implements PulpogatoType {
    /**
     * Creates an empty object without any properties.
     */
    public EmptyObject() {
        // Empty Default Constructor
    }

    @Override
    public String toCode() {
        return "new io.github.pulpogato.common.EmptyObject()";
    }
}
