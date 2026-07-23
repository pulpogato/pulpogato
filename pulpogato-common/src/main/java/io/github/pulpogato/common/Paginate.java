package io.github.pulpogato.common;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
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
            final LongFunction<@Nullable R> fetchPage,
            final Function<R, Stream<T>> extractItems,
            final ToIntFunction<R> totalPages) {
        return from(maxPages, fetchPage, extractItems, (page, response) -> page < totalPages.applyAsInt(response));
    }

    /**
     * Creates a stream of items from paginated API responses that don't report a total page count,
     * such as {@code io.github.pulpogato.rest.api.ReposApi#listBranches}. Pages are fetched lazily,
     * starting from page 1, and fetching stops as soon as a page comes back empty (or {@code maxPages}
     * is reached), since these APIs signal the end of pagination by returning a short or empty page.
     *
     * <p>Because there's no other signal available here, a non-empty page can't be distinguished
     * from the last page happening to end exactly on a page boundary, so this method always fetches
     * one extra page after any non-empty page to confirm there's no more data. Prefer
     * {@link #from(long, LongFunction, BiPredicate)} when a cheaper signal is available, such as
     * the requested page size or a response header, to avoid that extra request.
     *
     * @param <T>       the type of items to be extracted from each page
     * @param maxPages  the maximum number of pages to fetch (prevents infinite pagination)
     * @param fetchPage function that takes a page number (1-based) and returns the list of items for that page
     * @return a stream containing all items from the fetched pages
     */
    public <T> Stream<T> from(final long maxPages, final LongFunction<@Nullable List<T>> fetchPage) {
        return from(maxPages, fetchPage, (page, items) -> !items.isEmpty());
    }

    /**
     * Creates a stream of items from paginated API responses whose need for a next page is decided
     * by {@code hasNextPage}, rather than a total page count or an empty trailing page. This lets a
     * caller stop after the last page without an extra request, using whatever signal is available
     * for a given response type {@code R} — for example, comparing the item count against the
     * requested page size, or reading a {@code Link} header off the response.
     *
     * @param <T>         the type of items to be extracted from each page
     * @param <R>         the type of the API response containing the paginated data
     * @param maxPages    the maximum number of pages to fetch (prevents infinite pagination)
     * @param fetchPage   function that takes a page number (1-based) and returns the API response for that page
     * @param extractItems function that takes an API response and returns a stream of items from that page
     * @param hasNextPage predicate given the page number just fetched and its response, returning
     *                    whether another page should be fetched
     * @return a stream containing all items from the fetched pages
     */
    public <T, R> Stream<T> from(
            final long maxPages,
            final LongFunction<@Nullable R> fetchPage,
            final Function<R, Stream<T>> extractItems,
            final BiPredicate<Long, R> hasNextPage) {
        Iterator<R> iterator = new PageStreamIterator<>(maxPages, fetchPage, hasNextPage);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                .filter(Objects::nonNull)
                .flatMap(extractItems);
    }

    /**
     * Convenience form of {@link #from(long, LongFunction, Function, BiPredicate)} for APIs that
     * return a plain list of items per page, with no separate response wrapper to extract from.
     *
     * @param <T>         the type of items to be extracted from each page
     * @param maxPages    the maximum number of pages to fetch (prevents infinite pagination)
     * @param fetchPage   function that takes a page number (1-based) and returns the list of items for that page
     * @param hasNextPage predicate given the page number just fetched and its items, returning
     *                    whether another page should be fetched — for example, {@code (page, items)
     *                    -> items.size() == perPage} to stop as soon as a short page is seen
     * @return a stream containing all items from the fetched pages
     */
    public <T> Stream<T> from(
            final long maxPages,
            final LongFunction<@Nullable List<T>> fetchPage,
            final BiPredicate<Long, List<T>> hasNextPage) {
        return from(maxPages, fetchPage, List::stream, hasNextPage);
    }

    /**
     * Convenience form of {@link #from(long, LongFunction, Function, BiPredicate)} for GitHub-style
     * list endpoints that report pagination via a {@code Link} response header (RFC 8288) instead of
     * a total page count or an empty trailing page, such as
     * {@code io.github.pulpogato.rest.api.ReposApi#listBranches}. Stops as soon as a page's
     * {@code Link} header stops reporting {@code rel="next"}. A {@code null} response body is treated
     * as an empty page rather than an error, since callers with stricter needs (such as
     * distinguishing a not-found first page) can check the body themselves before returning.
     *
     * @param <T>       the type of items to be extracted from each page
     * @param maxPages  the maximum number of pages to fetch (prevents infinite pagination)
     * @param fetchPage function that takes a page number (1-based) and returns the response for that page
     * @return a stream containing all items from the fetched pages
     */
    public <T> Stream<T> fromLinkHeader(
            final long maxPages, final LongFunction<@Nullable ResponseEntity<List<T>>> fetchPage) {
        return from(
                maxPages,
                fetchPage,
                response -> {
                    List<T> body = response.getBody();
                    return body == null ? Stream.empty() : body.stream();
                },
                (page, response) -> hasNextPage(response));
    }

    /**
     * Matches a {@code rel} link-param naming {@code next}, per RFC 8288: either the quoted form
     * ({@code rel="next"}, optionally alongside other space-separated relation types) or the
     * unquoted extension-token form ({@code rel=next}). Relation types are case-insensitive.
     */
    private static final Pattern REL_NEXT = Pattern.compile("(?i)rel\\s*=\\s*(\"[^\"]*\\bnext\\b[^\"]*\"|next\\b)");

    private static boolean hasNextPage(final ResponseEntity<?> response) {
        String link = response.getHeaders().getFirst("Link");
        return link != null && REL_NEXT.matcher(link).find();
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
            final LongFunction<@Nullable Mono<R>> fetchPage,
            final Function<R, Flux<T>> extractItems,
            final ToIntFunction<R> totalPages) {
        return fetchReactive(
                maxPages, fetchPage, extractItems, (page, response) -> page < totalPages.applyAsInt(response));
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
    public <T> Flux<T> fromReactive(final long maxPages, final LongFunction<@Nullable Mono<List<T>>> fetchPage) {
        return fromReactive(maxPages, fetchPage, (page, items) -> !items.isEmpty());
    }

    /**
     * Reactive counterpart to {@link #from(long, LongFunction, Function, BiPredicate)}, for use with
     * reactive API clients (such as {@code io.github.pulpogato.rest.api.reactive.ReposApi}) whose
     * methods return a {@link Mono} instead of a value. Pages are fetched lazily and sequentially,
     * and {@code hasNextPage} decides — from whatever signal is available for response type
     * {@code R}, such as item count or a response header — whether another page should be fetched.
     *
     * @param <T>          the type of items to be extracted from each page
     * @param <R>          the type of the API response containing the paginated data
     * @param maxPages     the maximum number of pages to fetch (prevents infinite pagination)
     * @param fetchPage    function that takes a page number (1-based) and returns the API response for that page
     * @param extractItems function that takes an API response and returns a flux of items from that page
     * @param hasNextPage  predicate given the page number just fetched and its response, returning
     *                     whether another page should be fetched
     * @return a flux containing all items from the fetched pages
     */
    public <T, R> Flux<T> fromReactive(
            final long maxPages,
            final LongFunction<@Nullable Mono<R>> fetchPage,
            final Function<R, Flux<T>> extractItems,
            final BiPredicate<Long, R> hasNextPage) {
        return fetchReactive(maxPages, fetchPage, extractItems, hasNextPage);
    }

    /**
     * Convenience form of {@link #fromReactive(long, LongFunction, Function, BiPredicate)} for APIs
     * that return a plain list of items per page, with no separate response wrapper to extract from.
     *
     * @param <T>         the type of items to be extracted from each page
     * @param maxPages    the maximum number of pages to fetch (prevents infinite pagination)
     * @param fetchPage   function that takes a page number (1-based) and returns the list of items for that page
     * @param hasNextPage predicate given the page number just fetched and its items, returning
     *                    whether another page should be fetched — for example, {@code (page, items)
     *                    -> items.size() == perPage} to stop as soon as a short page is seen
     * @return a flux containing all items from the fetched pages
     */
    public <T> Flux<T> fromReactive(
            final long maxPages,
            final LongFunction<@Nullable Mono<List<T>>> fetchPage,
            final BiPredicate<Long, List<T>> hasNextPage) {
        return fetchReactive(maxPages, fetchPage, Flux::fromIterable, hasNextPage);
    }

    /**
     * Reactive counterpart to {@link #fromLinkHeader(long, LongFunction)}, for use with reactive API
     * clients (such as {@code io.github.pulpogato.rest.api.reactive.ReposApi}) whose methods return a
     * {@link Mono} instead of a value. Stops as soon as a page's {@code Link} header stops reporting
     * {@code rel="next"}. A {@code null} response body is treated as an empty page rather than an
     * error, since callers with stricter needs (such as distinguishing a not-found first page) can
     * check the body themselves before returning.
     *
     * @param <T>       the type of items to be extracted from each page
     * @param maxPages  the maximum number of pages to fetch (prevents infinite pagination)
     * @param fetchPage function that takes a page number (1-based) and returns the response for that page
     * @return a flux containing all items from the fetched pages
     */
    public <T> Flux<T> fromLinkHeaderReactive(
            final long maxPages, final LongFunction<@Nullable Mono<ResponseEntity<List<T>>>> fetchPage) {
        return fetchReactive(
                maxPages,
                fetchPage,
                response -> {
                    List<T> body = response.getBody();
                    return body == null ? Flux.empty() : Flux.fromIterable(body);
                },
                (page, response) -> hasNextPage(response));
    }

    /**
     * Shared implementation for reactive pagination. Fetches pages sequentially starting at page 1,
     * emitting items from each page before requesting the next. Fetching stops when {@code maxPages}
     * is reached, when {@code hasMorePages} returns {@code false} for the current page, or when
     * {@code fetchPage} returns {@code null}. Pages are unfolded via {@link Mono#expand}, which
     * fetches at most one page ahead of what has been emitted — without growing the call stack per
     * page the way a self-recursive method call would, so an unbounded {@code maxPages} can't
     * overflow the stack even if every page resolves synchronously.
     *
     * @param <T>          the type of items to be extracted from each page
     * @param <R>          the type of the API response containing the paginated data
     * @param maxPages     the maximum number of pages to fetch (prevents infinite pagination)
     * @param fetchPage    function that takes a page number and returns the API response for that page
     * @param extractItems function that takes an API response and returns a flux of items from that page
     * @param hasMorePages predicate that returns {@code true} when another page should be fetched after
     *                     the current one (for example, when the response reports more total pages, or
     *                     when a list-only page is non-empty)
     * @return a flux containing all items from the fetched pages
     */
    private <T, R> Flux<T> fetchReactive(
            final long maxPages,
            final LongFunction<@Nullable Mono<R>> fetchPage,
            final Function<R, Flux<T>> extractItems,
            final BiPredicate<Long, R> hasMorePages) {
        return fetchPageOrEmpty(fetchPage, 1L)
                .map(response -> new PageResult<>(1L, response))
                .expand(current -> {
                    if (current.page() >= maxPages || !hasMorePages.test(current.page(), current.response())) {
                        return Mono.empty();
                    }
                    var nextPage = current.page() + 1;
                    return fetchPageOrEmpty(fetchPage, nextPage).map(response -> new PageResult<>(nextPage, response));
                })
                .concatMap(result -> extractItems.apply(result.response()));
    }

    private static <R> Mono<R> fetchPageOrEmpty(final LongFunction<@Nullable Mono<R>> fetchPage, final long page) {
        Mono<R> pageContent = fetchPage.apply(page);
        return pageContent == null ? Mono.empty() : pageContent;
    }

    private record PageResult<R>(long page, R response) {}

    @RequiredArgsConstructor
    private static class PageStreamIterator<R> implements Iterator<R> {
        private final long maxPages;
        private final LongFunction<@Nullable R> fetchPage;
        private final BiPredicate<Long, R> hasNextPage;
        private long page = 1;
        private boolean done = false;

        @Override
        public boolean hasNext() {
            return !done && page <= maxPages;
        }

        @Override
        @Nullable
        public R next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            var currentPage = page++;
            var response = fetchPage.apply(currentPage);
            if (response == null || !hasNextPage.test(currentPage, response)) {
                done = true;
            }
            return response;
        }
    }
}
