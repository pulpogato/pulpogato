/**
 * Shared types and utilities used across all Pulpogato modules.
 *
 * <p>This package provides foundational abstractions for working with GitHub API data:
 * <ul>
 *   <li>{@link io.github.pulpogato.common.NullableOptional} — three-state wrapper that
 *       distinguishes absent fields from explicit {@code null} values in JSON</li>
 *   <li>{@link io.github.pulpogato.common.PulpogatoType} — base interface for types that
 *       can render themselves as Java source code</li>
 *   <li>{@link io.github.pulpogato.common.WebhookEvent} — marker interface for webhook
 *       event payload types</li>
 *   <li>{@link io.github.pulpogato.common.Mode} — controls how OpenAPI {@code oneOf},
 *       {@code anyOf}, and {@code allOf} schemas are deserialized</li>
 * </ul>
 *
 * <p>Related packages:
 * <ul>
 *   <li>{@link io.github.pulpogato.common.client} — HTTP client filters and interceptors</li>
 *   <li>{@link io.github.pulpogato.common.cache} — RFC 9111 conditional HTTP caching</li>
 *   <li>{@link io.github.pulpogato.common.jackson} — Jackson serializers and deserializers</li>
 *   <li>{@link io.github.pulpogato.common.util} — code-generation helpers</li>
 * </ul>
 */
@NullMarked
package io.github.pulpogato.common;

import org.jspecify.annotations.NullMarked;
