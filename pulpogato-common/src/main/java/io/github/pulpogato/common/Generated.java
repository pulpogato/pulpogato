package io.github.pulpogato.common;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicates that the class was generated.
 */
@SuppressWarnings("unused")
@Retention(RetentionPolicy.RUNTIME)
public @interface Generated {
    /**
     * The version of the GitHub API Spec
     * @return The version of the GitHub API Spec
     */
    String ghVersion();
    /**
     * The location of the type in the schema
     * @return The location as a json reference
     */
    String schemaRef() default "";
    /**
     * The generator of the class
     * @return The file and line that generated the type
     */
    String codeRef();
}
