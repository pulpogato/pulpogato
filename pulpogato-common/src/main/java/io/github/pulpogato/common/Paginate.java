package io.github.pulpogato.common;

import org.jspecify.annotations.NonNull;

import java.util.function.Function;
import java.util.stream.Stream;

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
            final Function<Long, @NonNull R> fetchPage,
            final Function<R, @NonNull Stream<T>> extractItems,
            final Function<R, @NonNull Integer> totalPages
    ) {
        var response = fetchPage.apply(1L);
        var pages = Math.min(maxPages, totalPages.apply(response));
        if (pages <= 1) {
            return extractItems.apply(response);
        }
        return Stream.concat(
                extractItems.apply(response),
                Stream.iterate(2L, page -> page + 1)
                        .limit(pages - 1)
                        .flatMap(page -> extractItems.apply(fetchPage.apply(page)))
        );
    }
}
