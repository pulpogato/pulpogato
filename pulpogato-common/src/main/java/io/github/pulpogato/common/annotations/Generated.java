package io.github.pulpogato.common.annotations;

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
    /**
     * The source file from which this element was generated
     * @return The source file name (e.g., "schema.json" or "additions.schema.json")
     */
    String sourceFile() default "schema.json";
}
