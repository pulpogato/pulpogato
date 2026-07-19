package io.github.pulpogato.common;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Utility class for paginating through API responses and collecting all items into a single stream.
 * This class provides a generic way to handle paginated data from any source by accepting
 * functions for fetching pages, extracting items, and determining total pages.
 */
public class Paginate {
    /**
     * Creates a new instance of the Paginate utility.
     */
    public Paginate() {
        // Empty Default Constructor
    }

    /**
     * Creates a stream of items from paginated API responses.
     *
     * @param <T>          the type of items to be extracted from each page
     * @param <R>          the type of the API response containing the paginated data
     * @param maxPages     the maximum number of pages to fetch (prevents infinite pagination)
     * @param fetchPage    function that takes a page number (1-based) and returns the API response for that page
     * @param extractItems function that takes an API response and returns a stream of items from that page
     * @param totalPages   function that takes an API response and returns the total number of pages available
     * @return a stream containing all items from the fetched pages
     */
    @NonNull
    public <T, R> Stream<T> from(
            final long maxPages,
            final LongFunction<@Nullable R> fetchPage,
            final Function<@NonNull R, @NonNull Stream<T>> extractItems,
            final ToIntFunction<@NonNull R> totalPages) {
        var response = fetchPage.apply(1L);
        if (response == null) {
            return Stream.empty();
        }
        var pages = Math.min(maxPages, totalPages.applyAsInt(response));
        var firstPage = extractItems.apply(response);
        if (pages <= 1) {
            return firstPage;
        }
        return Stream.concat(
                firstPage, Stream.iterate(2L, page -> page + 1).limit(pages - 1).flatMap(page -> {
                    var pageResponse = fetchPage.apply(page);
                    if (pageResponse == null) {
                        return Stream.empty();
                    }
                    return extractItems.apply(pageResponse);
                }));
    }

    /**
     * Creates a stream of items from paginated API responses that don't report a total page count,
     * such as {@code io.github.pulpogato.rest.api.ReposApi#listBranches}. Pages are fetched lazily,
     * starting from page 1, and fetching stops as soon as a page comes back empty (or {@code maxPages}
     * is reached), since these APIs signal the end of pagination by returning a short or empty page.
     *
     * @param <T>       the type of items to be extracted from each page
     * @param maxPages  the maximum number of pages to fetch (prevents infinite pagination)
     * @param fetchPage function that takes a page number (1-based) and returns the list of items for that page
     * @return a stream containing all items from the fetched pages
     */
    @NonNull
    public <T> Stream<T> from(final long maxPages, final LongFunction<@Nullable List<T>> fetchPage) {
        return Stream.iterate(1L, page -> page + 1)
                .limit(maxPages)
                .map(fetchPage::apply)
                .takeWhile(items -> items != null && !items.isEmpty())
                .flatMap(Collection::stream);
    }

    /**
     * Reactive counterpart to {@link #from(long, LongFunction, Function, ToIntFunction)}, for use with
     * reactive API clients (such as {@code io.github.pulpogato.rest.api.reactive.SearchApi}) whose methods
     * return a {@link Mono} instead of a value. Pages are fetched lazily and sequentially, one at a time,
     * up to {@code maxPages} or the total page count reported by the first response, whichever is lower.
     *
     * @param <T>          the type of items to be extracted from each page
     * @param <R>          the type of the API response containing the paginated data
     * @param maxPages     the maximum number of pages to fetch (prevents infinite pagination)
     * @param fetchPage    function that takes a page number (1-based) and returns the API response for that page
     * @param extractItems function that takes an API response and returns a flux of items from that page
     * @param totalPages   function that takes an API response and returns the total number of pages available
     * @return a flux containing all items from the fetched pages
     */
    @NonNull
    public <T, R> Flux<T> fromReactive(
            final long maxPages,
            final LongFunction<@Nullable Mono<R>> fetchPage,
            final Function<@NonNull R, @NonNull Flux<T>> extractItems,
            final ToIntFunction<@NonNull R> totalPages) {
        return fetchReactive(1L, maxPages, fetchPage, extractItems, totalPages);
    }

    private <T, R> Flux<T> fetchReactive(
            final long page,
            final long maxPages,
            final LongFunction<@Nullable Mono<R>> fetchPage,
            final Function<@NonNull R, @NonNull Flux<T>> extractItems,
            final ToIntFunction<@NonNull R> totalPages) {
        Mono<R> pageContent = fetchPage.apply(page);
        if (pageContent == null) {
            return Flux.empty();
        }
        return pageContent.flatMapMany(response -> {
            var items = extractItems.apply(response);
            if (page >= maxPages || page >= totalPages.applyAsInt(response)) {
                return items;
            }
            return Flux.concat(items, fetchReactive(page + 1, maxPages, fetchPage, extractItems, totalPages));
        });
    }

    /**
     * Reactive counterpart to {@link #from(long, LongFunction)}, for use with reactive API clients (such as
     * {@code io.github.pulpogato.rest.api.reactive.ReposApi}) whose methods return a {@link Mono} instead of
     * a value. Pages are fetched lazily and sequentially, starting from page 1, and fetching stops as soon
     * as a page comes back empty (or {@code maxPages} is reached).
     *
     * @param <T>       the type of items to be extracted from each page
     * @param maxPages  the maximum number of pages to fetch (prevents infinite pagination)
     * @param fetchPage function that takes a page number (1-based) and returns the list of items for that page
     * @return a flux containing all items from the fetched pages
     */
    @NonNull
    public <T> Flux<T> fromReactive(final long maxPages, final LongFunction<@Nullable Mono<List<T>>> fetchPage) {
        return fetchListReactive(1L, maxPages, fetchPage);
    }

    private <T> Flux<T> fetchListReactive(
            final long page, final long maxPages, final LongFunction<@Nullable Mono<List<T>>> fetchPage) {
        if (page > maxPages) {
            return Flux.empty();
        }
        Mono<List<T>> pageContent = fetchPage.apply(page);
        if (pageContent == null) {
            return Flux.empty();
        }
        return pageContent.flatMapMany(items -> {
            if (items.isEmpty()) {
                return Flux.empty();
            }
            return Flux.concat(Flux.fromIterable(items), fetchListReactive(page + 1, maxPages, fetchPage));
        });
    }
}
