/**
 * Jackson serializers and deserializers for Pulpogato-generated types.
 *
 * <p>Supports both Jackson 2 ({@code com.fasterxml.jackson}) and Jackson 3
 * ({@code tools.jackson}) with parallel class pairs, since different Pulpogato modules
 * may target either version.
 *
 * <p>Key components:
 * <ul>
 *   <li>{@code *FancyDeserializer} — handles OpenAPI {@code oneOf}, {@code anyOf}, and
 *       {@code allOf} schemas, delegating to {@link io.github.pulpogato.common.jackson.FancyDeserializerSupport}</li>
 *   <li>{@code *LenientFancyDeserializer} — same as above but tolerates unknown properties</li>
 *   <li>{@code NullableOptional*Serializer/Deserializer} — serializes
 *       {@link io.github.pulpogato.common.NullableOptional} three-state fields</li>
 *   <li>{@code OffsetDateTime*Deserializer} — parses GitHub's date-time format</li>
 * </ul>
 */
@NullMarked
package io.github.pulpogato.common.jackson;

import org.jspecify.annotations.NullMarked;
