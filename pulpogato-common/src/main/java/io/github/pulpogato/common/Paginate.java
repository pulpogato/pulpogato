package io.github.pulpogato.common;

import java.util.List;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
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
    public <T, R> Stream<T> from(
            final long maxPages,
            final LongFunction<@NonNull R> fetchPage,
            final Function<R, @NonNull Stream<T>> extractItems,
            final ToIntFunction<R> totalPages) {
        var response = fetchPage.apply(1L);
        var pages = Math.min(maxPages, totalPages.applyAsInt(response));
        if (pages <= 1) {
            return extractItems.apply(response);
        }
        return Stream.concat(
                extractItems.apply(response),
                Stream.iterate(2L, page -> page + 1)
                        .limit(pages - 1)
                        .flatMap(page -> extractItems.apply(fetchPage.apply(page))));
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
    public <T> Stream<T> from(final long maxPages, final LongFunction<@NonNull List<T>> fetchPage) {
        return Stream.iterate(1L, page -> page + 1)
                .limit(maxPages)
                .map(fetchPage::apply)
                .takeWhile(items -> !items.isEmpty())
                .flatMap(List::stream);
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
    public <T, R> Flux<T> fromReactive(
            final long maxPages,
            final LongFunction<@NonNull Mono<R>> fetchPage,
            final Function<R, @NonNull Flux<T>> extractItems,
            final ToIntFunction<R> totalPages) {
        return fetchReactive(1L, maxPages, fetchPage, extractItems, totalPages);
    }

    private <T, R> Flux<T> fetchReactive(
            final long page,
            final long maxPages,
            final LongFunction<@NonNull Mono<R>> fetchPage,
            final Function<R, @NonNull Flux<T>> extractItems,
            final ToIntFunction<R> totalPages) {
        return fetchPage.apply(page).flatMapMany(response -> {
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
    public <T> Flux<T> fromReactive(final long maxPages, final LongFunction<@NonNull Mono<List<T>>> fetchPage) {
        return fetchListReactive(1L, maxPages, fetchPage);
    }

    private <T> Flux<T> fetchListReactive(
            final long page, final long maxPages, final LongFunction<@NonNull Mono<List<T>>> fetchPage) {
        if (page > maxPages) {
            return Flux.empty();
        }
        return fetchPage.apply(page).flatMapMany(items -> {
            if (items.isEmpty()) {
                return Flux.empty();
            }
            return Flux.concat(Flux.fromIterable(items), fetchListReactive(page + 1, maxPages, fetchPage));
        });
    }
}
