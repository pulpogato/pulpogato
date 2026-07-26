package io.github.pulpogato.common.jackson;

import org.jspecify.annotations.Nullable;

/**
 * Represents a function that accepts one argument and produces a nullable result.
 *
 * <p>This is a functional interface whose functional method is {@link #apply(Object)}.
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 */
@FunctionalInterface
public interface NullableFunction<T, R> {
    /**
     * Applies this function to the given argument.
     *
     * @param t the function argument
     * @return the function result, which may be {@code null}
     */
    @Nullable
    R apply(T t);
}
