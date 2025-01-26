package io.github.pulpogato.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Indicates that the type was generated.
 */
@Target(ElementType.TYPE_USE)
public @interface TypeGenerated {
    /**
     * The location of the type in the schema
     * @return The location as a json reference
     */
    String codeRef();
}
