/**
 * Utilities for generating Java source code from Pulpogato types.
 *
 * <p>These helpers support {@link io.github.pulpogato.common.PulpogatoType#toCode()}, which
 * renders an object as compilable Java source — useful in tests, examples, and tooling.
 *
 * <ul>
 *   <li>{@link io.github.pulpogato.common.util.CodeBuilder} — builds fluent builder-style
 *       code strings for arbitrary types</li>
 *   <li>{@link io.github.pulpogato.common.util.LinkedHashMapBuilder} — constructs
 *       insertion-ordered maps for use in generated code</li>
 * </ul>
 */
@NullMarked
package io.github.pulpogato.common.util;

import org.jspecify.annotations.NullMarked;
