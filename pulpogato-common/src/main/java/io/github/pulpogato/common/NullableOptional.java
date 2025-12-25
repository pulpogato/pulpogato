package io.github.pulpogato.common;

import io.github.pulpogato.common.jackson.NullableOptionalJackson2Deserializer;
import io.github.pulpogato.common.jackson.NullableOptionalJackson2Serializer;
import io.github.pulpogato.common.jackson.NullableOptionalJackson3Deserializer;
import io.github.pulpogato.common.jackson.NullableOptionalJackson3Serializer;
import io.github.pulpogato.common.util.CodeBuilder;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.EqualsAndHashCode;

/**
 * A three-state wrapper for nullable fields that distinguishes between:
 * <ul>
 *   <li><b>NOT_SET</b>: Field is absent, not serialized to JSON</li>
 *   <li><b>NULL</b>: Field is explicitly null, serialized as "field": null</li>
 *   <li><b>VALUE</b>: Field has a value, serialized normally</li>
 * </ul>
 *
 * <p>This class is necessary because:
 * <ul>
 *   <li>Java's {@code Optional<T>} cannot hold null values</li>
 *   <li>GitHub API distinguishes between absent fields (no change) and null fields (unset value)</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Field not set - omitted from JSON
 * NullableOptional<String> notSet = NullableOptional.notSet();
 *
 * // Field explicitly set to null - serialized as null in JSON
 * NullableOptional<String> explicitNull = NullableOptional.ofNull();
 *
 * // Field has a value - serialized with value in JSON
 * NullableOptional<String> hasValue = NullableOptional.of("example");
 * }</pre>
 *
 * @param <T> the type of value that can be held
 */
@tools.jackson.databind.annotation.JsonSerialize(using = NullableOptionalJackson3Serializer.class)
@tools.jackson.databind.annotation.JsonDeserialize(using = NullableOptionalJackson3Deserializer.class)
@com.fasterxml.jackson.databind.annotation.JsonSerialize(using = NullableOptionalJackson2Serializer.class)
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = NullableOptionalJackson2Deserializer.class)
@EqualsAndHashCode
public final class NullableOptional<T> implements PulpogatoType {

    private enum State {
        NOT_SET,
        NULL,
        VALUE
    }

    private final State state;
    private final T value;

    private NullableOptional(State state, T value) {
        this.state = state;
        this.value = value;
    }

    /**
     * Returns a NullableOptional with no value set.
     * When serialized to JSON, the field will be absent (omitted).
     *
     * @param <T> the type of the value
     * @return a NullableOptional in NOT_SET state
     */
    public static <T> NullableOptional<T> notSet() {
        return new NullableOptional<>(State.NOT_SET, null);
    }

    /**
     * Returns a NullableOptional explicitly set to null.
     * When serialized to JSON, the field will be present with null value.
     *
     * @param <T> the type of the value
     * @return a NullableOptional in NULL state
     */
    public static <T> NullableOptional<T> ofNull() {
        return new NullableOptional<>(State.NULL, null);
    }

    /**
     * Returns a NullableOptional with the specified non-null value.
     * When serialized to JSON, the field will be present with the value.
     *
     * @param value the non-null value to wrap
     * @param <T> the type of the value
     * @return a NullableOptional in VALUE state
     * @throws IllegalArgumentException if value is null (use ofNull() instead)
     */
    public static <T> NullableOptional<T> of(T value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null. Use ofNull() for explicit null.");
        }
        return new NullableOptional<>(State.VALUE, value);
    }

    /**
     * <p>
     * Returns a NullableOptional for the given value, which may be null.
     * If the value is null, returns a NULL state NullableOptional.
     * If the value is non-null, returns a VALUE state NullableOptional.
     * </p>
     * <p>
     * This is useful in builder patterns where the caller has a potentially
     * null value and wants it automatically wrapped appropriately.
     * </p>
     * @param value the value to wrap (may be null)
     * @param <T> the type of the value
     * @return a NullableOptional in NULL state if value is null, VALUE state otherwise
     */
    public static <T> NullableOptional<T> ofNullable(T value) {
        return value == null ? ofNull() : of(value);
    }

    /**
     * Returns true if this NullableOptional is not set (absent).
     *
     * @return true if NOT_SET, false otherwise
     */
    public boolean isNotSet() {
        return state == State.NOT_SET;
    }

    /**
     * Returns true if this NullableOptional is explicitly set to null.
     *
     * @return true if NULL, false otherwise
     */
    public boolean isNull() {
        return state == State.NULL;
    }

    /**
     * Returns true if this NullableOptional has a value.
     *
     * @return true if VALUE, false otherwise
     */
    public boolean isValue() {
        return state == State.VALUE;
    }

    /**
     * Returns the value if present.
     *
     * @return the value
     * @throws IllegalStateException if the value is not present (NOT_SET or NULL)
     */
    public T getValue() {
        if (state != State.VALUE) {
            throw new IllegalStateException("No value present. State is: " + state);
        }
        return value;
    }

    /**
     * Returns the value if present, otherwise returns the specified default value.
     *
     * @param defaultValue the value to return if no value is present
     * @return the value if present, otherwise defaultValue
     */
    public T orElse(T defaultValue) {
        return state == State.VALUE ? value : defaultValue;
    }

    /**
     * Returns the value if present, otherwise returns null.
     *
     * @return the value if present, otherwise null
     */
    public T orElseNull() {
        return state == State.VALUE ? value : null;
    }

    /**
     * If a value is present, performs the given action with the value.
     *
     * @param consumer the action to perform
     */
    public void ifValue(Consumer<? super T> consumer) {
        if (state == State.VALUE) {
            consumer.accept(value);
        }
    }

    /**
     * If a value is present, applies the mapping function and returns a NullableOptional
     * wrapping the result. Otherwise, returns a NOT_SET NullableOptional.
     *
     * @param mapper the mapping function
     * @param <U> the type of the result
     * @return a NullableOptional with the mapped value or NOT_SET
     */
    public <U> NullableOptional<U> map(Function<? super T, ? extends U> mapper) {
        return switch (state) {
            case VALUE -> NullableOptional.of(mapper.apply(value));
            case NULL -> NullableOptional.ofNull();
            case null, default -> NullableOptional.notSet();
        };
    }

    @Override
    public String toCode() {
        if (isNotSet()) {
            return "io.github.pulpogato.common.NullableOptional.notSet()";
        } else if (isNull()) {
            return "io.github.pulpogato.common.NullableOptional.ofNull()";
        } else if (value instanceof PulpogatoType pt) {
            return "io.github.pulpogato.common.NullableOptional.of(" + pt.toCode() + ")";
        } else {
            return "io.github.pulpogato.common.NullableOptional.of(" + CodeBuilder.render(value) + ")";
        }
    }

    @Override
    public String toString() {
        return switch (state) {
            case NOT_SET -> "NullableOptional.notSet()";
            case NULL -> "NullableOptional.ofNull()";
            case VALUE -> "NullableOptional[" + value + "]";
        };
    }
}
