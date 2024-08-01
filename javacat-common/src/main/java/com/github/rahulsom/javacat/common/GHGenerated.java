package com.github.rahulsom.javacat.common;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicates that the class was generated.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface GHGenerated {
    /**
     * The location of the type in the schema
     * @return The location as a json reference
     */
    String from();
    /**
     * The generator of the class
     * @return The file and line that generated the type
     */
    String by();
}
