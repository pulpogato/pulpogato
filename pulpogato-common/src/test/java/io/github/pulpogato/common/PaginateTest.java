package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaginateTest {

    record Response(List<String> items, int totalPages) {}

    @Mock
    private Function<Long, Response> fetchPage;

    @Test
    @DisplayName("Should return all items from a single page")
    void singlePage() {
        var paginate = new Paginate();
        when(fetchPage.apply(1L)).thenReturn(new Response(List.of("1", "2", "3"), 1));

        var result = paginate.from(10, fetchPage, PaginateTest::getStream, Response::totalPages);
        assertThat(result).containsExactly("1", "2", "3");
    }

    private static Stream<String> getStream(Response response) {
        return response.items().stream();
    }

    @Test
    @DisplayName("Should concatenate items from multiple pages")
    void multiplePages() {
        var paginate = new Paginate();
        when(fetchPage.apply(1L)).thenReturn(new Response(List.of("1", "2", "3"), 3));
        when(fetchPage.apply(2L)).thenReturn(new Response(List.of("4", "5", "6"), 3));
        when(fetchPage.apply(3L)).thenReturn(new Response(List.of("7", "8", "9"), 3));

        var result = paginate.from(10, fetchPage, PaginateTest::getStream, Response::totalPages);
        assertThat(result).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
    }

    @Test
    @DisplayName("Should return empty stream when there is no data")
    void noData() {
        var paginate = new Paginate();
        when(fetchPage.apply(1L)).thenReturn(new Response(List.of(), 0));

        var result = paginate.from(10, fetchPage, PaginateTest::getStream, Response::totalPages);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should respect max pages limit when total pages exceed it")
    void limitPages() {
        var paginate = new Paginate();
        when(fetchPage.apply(1L)).thenReturn(new Response(List.of("1", "2", "3"), 5));
        when(fetchPage.apply(2L)).thenReturn(new Response(List.of("4", "5", "6"), 5));
        when(fetchPage.apply(3L)).thenReturn(new Response(List.of("7", "8", "9"), 5));

        var result = paginate.from(3, fetchPage, PaginateTest::getStream, Response::totalPages);
        assertThat(result).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
    }

    @Test
    @DisplayName("Should propagate exception thrown during first page fetch")
    void throwExceptionOnFirstPage() {
        var paginate = new Paginate();
        when(fetchPage.apply(1L)).thenThrow(new RuntimeException("Failed to fetch page"));

        assertThatThrownBy(() -> paginate.from(10, fetchPage, PaginateTest::getStream, Response::totalPages)
                        .toList())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to fetch page");
    }

    @Test
    @DisplayName("Should propagate exception thrown during subsequent page fetch")
    void throwExceptionOnSubsequentPage() {
        var paginate = new Paginate();
        when(fetchPage.apply(1L)).thenReturn(new Response(List.of("1", "2", "3"), 3));
        when(fetchPage.apply(2L)).thenThrow(new RuntimeException("Failed to fetch page"));

        assertThatThrownBy(() -> paginate.from(10, fetchPage, PaginateTest::getStream, Response::totalPages)
                        .toList())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to fetch page");
    }

    @Test
    @DisplayName("Should stop fetching pages when stream consumer terminates early")
    void stopsReadingIfConsumerStops() {
        var paginate = new Paginate();
        when(fetchPage.apply(1L)).thenReturn(new Response(List.of("1", "2", "3"), 5));
        when(fetchPage.apply(2L)).thenReturn(new Response(List.of("4", "5", "6"), 5));

        var result = paginate.from(3, fetchPage, PaginateTest::getStream, Response::totalPages);

        final var first = result.filter("5"::equals).findFirst();

        assertThat(first).isPresent();
    }
}
