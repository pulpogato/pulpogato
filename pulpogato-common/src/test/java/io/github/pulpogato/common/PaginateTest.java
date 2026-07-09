package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.LongFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PaginateTest {

    record Response(List<String> items, int totalPages) {}

    private static Stream<String> getStream(Response response) {
        return response.items().stream();
    }

    private static Flux<String> getFlux(Response response) {
        return Flux.fromIterable(response.items());
    }

    @Nested
    @DisplayName("from(maxPages, fetchPage, extractItems, totalPages)")
    class TotalPagesBased {

        @Mock
        private LongFunction<Response> fetchPage;

        @Test
        @DisplayName("Should return all items from a single page")
        void singlePage() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(new Response(List.of("1", "2", "3"), 1));

            var result = paginate.from(10, fetchPage, PaginateTest::getStream, Response::totalPages);
            assertThat(result).containsExactly("1", "2", "3");
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

            var stream = paginate.from(10, fetchPage, PaginateTest::getStream, Response::totalPages);
            assertThatThrownBy(stream::toList)
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

    @Nested
    @DisplayName("from(maxPages, fetchPage)")
    class ListOnlyBased {

        @Mock
        private LongFunction<List<String>> fetchListPage;

        @Test
        @DisplayName("Should return all items from a single page when there is no total page count")
        void singlePageNoTotalPages() {
            var paginate = new Paginate();
            when(fetchListPage.apply(1L)).thenReturn(List.of("1", "2", "3"));
            when(fetchListPage.apply(2L)).thenReturn(List.of());

            var result = paginate.from(10, fetchListPage);
            assertThat(result).containsExactly("1", "2", "3");
        }

        @Test
        @DisplayName("Should concatenate items from multiple pages when there is no total page count")
        void multiplePagesNoTotalPages() {
            var paginate = new Paginate();
            when(fetchListPage.apply(1L)).thenReturn(List.of("1", "2", "3"));
            when(fetchListPage.apply(2L)).thenReturn(List.of("4", "5", "6"));
            when(fetchListPage.apply(3L)).thenReturn(List.of());

            var result = paginate.from(10, fetchListPage);
            assertThat(result).containsExactly("1", "2", "3", "4", "5", "6");
        }

        @Test
        @DisplayName("Should return empty stream when there is no data and no total page count")
        void noDataNoTotalPages() {
            var paginate = new Paginate();
            when(fetchListPage.apply(1L)).thenReturn(List.of());

            var result = paginate.from(10, fetchListPage);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should respect max pages limit when there is no total page count")
        void limitPagesNoTotalPages() {
            var paginate = new Paginate();
            when(fetchListPage.apply(1L)).thenReturn(List.of("1", "2", "3"));
            when(fetchListPage.apply(2L)).thenReturn(List.of("4", "5", "6"));

            var result = paginate.from(2, fetchListPage);
            assertThat(result).containsExactly("1", "2", "3", "4", "5", "6");
        }
    }

    @Nested
    @DisplayName("fromReactive(maxPages, fetchPage, extractItems, totalPages)")
    class TotalPagesBasedReactive {

        @Mock
        private LongFunction<Mono<Response>> fetchReactivePage;

        @Test
        @DisplayName("Should return all items from a single page reactively")
        void singlePageReactive() {
            var paginate = new Paginate();
            when(fetchReactivePage.apply(1L)).thenReturn(Mono.just(new Response(List.of("1", "2", "3"), 1)));

            var result = paginate.fromReactive(10, fetchReactivePage, PaginateTest::getFlux, Response::totalPages);
            StepVerifier.create(result).expectNext("1", "2", "3").verifyComplete();
        }

        @Test
        @DisplayName("Should concatenate items from multiple pages reactively")
        void multiplePagesReactive() {
            var paginate = new Paginate();
            when(fetchReactivePage.apply(1L)).thenReturn(Mono.just(new Response(List.of("1", "2", "3"), 3)));
            when(fetchReactivePage.apply(2L)).thenReturn(Mono.just(new Response(List.of("4", "5", "6"), 3)));
            when(fetchReactivePage.apply(3L)).thenReturn(Mono.just(new Response(List.of("7", "8", "9"), 3)));

            var result = paginate.fromReactive(10, fetchReactivePage, PaginateTest::getFlux, Response::totalPages);
            StepVerifier.create(result)
                    .expectNext("1", "2", "3", "4", "5", "6", "7", "8", "9")
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty flux when there is no data")
        void noDataReactive() {
            var paginate = new Paginate();
            when(fetchReactivePage.apply(1L)).thenReturn(Mono.just(new Response(List.of(), 0)));

            var result = paginate.fromReactive(10, fetchReactivePage, PaginateTest::getFlux, Response::totalPages);
            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("Should respect max pages limit when total pages exceed it reactively")
        void limitPagesReactive() {
            var paginate = new Paginate();
            when(fetchReactivePage.apply(1L)).thenReturn(Mono.just(new Response(List.of("1", "2", "3"), 5)));
            when(fetchReactivePage.apply(2L)).thenReturn(Mono.just(new Response(List.of("4", "5", "6"), 5)));
            when(fetchReactivePage.apply(3L)).thenReturn(Mono.just(new Response(List.of("7", "8", "9"), 5)));

            var result = paginate.fromReactive(3, fetchReactivePage, PaginateTest::getFlux, Response::totalPages);
            StepVerifier.create(result)
                    .expectNext("1", "2", "3", "4", "5", "6", "7", "8", "9")
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should propagate error thrown during first page fetch reactively")
        void errorOnFirstPageReactive() {
            var paginate = new Paginate();
            when(fetchReactivePage.apply(1L)).thenReturn(Mono.error(new RuntimeException("Failed to fetch page")));

            var result = paginate.fromReactive(10, fetchReactivePage, PaginateTest::getFlux, Response::totalPages);
            StepVerifier.create(result).verifyErrorMessage("Failed to fetch page");
        }

        @Test
        @DisplayName("Should propagate error thrown during subsequent page fetch reactively")
        void errorOnSubsequentPageReactive() {
            var paginate = new Paginate();
            when(fetchReactivePage.apply(1L)).thenReturn(Mono.just(new Response(List.of("1", "2", "3"), 3)));
            when(fetchReactivePage.apply(2L)).thenReturn(Mono.error(new RuntimeException("Failed to fetch page")));

            var result = paginate.fromReactive(10, fetchReactivePage, PaginateTest::getFlux, Response::totalPages);
            StepVerifier.create(result).expectNext("1", "2", "3").verifyErrorMessage("Failed to fetch page");
        }
    }

    @Nested
    @DisplayName("fromReactive(maxPages, fetchPage)")
    class ListOnlyBasedReactive {

        @Mock
        private LongFunction<Mono<List<String>>> fetchListReactivePage;

        @Test
        @DisplayName("Should return all items from a single page reactively when there is no total page count")
        void singlePageNoTotalPagesReactive() {
            var paginate = new Paginate();
            when(fetchListReactivePage.apply(1L)).thenReturn(Mono.just(List.of("1", "2", "3")));
            when(fetchListReactivePage.apply(2L)).thenReturn(Mono.just(List.of()));

            var result = paginate.fromReactive(10, fetchListReactivePage);
            StepVerifier.create(result).expectNext("1", "2", "3").verifyComplete();
        }

        @Test
        @DisplayName("Should concatenate items from multiple pages reactively when there is no total page count")
        void multiplePagesNoTotalPagesReactive() {
            var paginate = new Paginate();
            when(fetchListReactivePage.apply(1L)).thenReturn(Mono.just(List.of("1", "2", "3")));
            when(fetchListReactivePage.apply(2L)).thenReturn(Mono.just(List.of("4", "5", "6")));
            when(fetchListReactivePage.apply(3L)).thenReturn(Mono.just(List.of()));

            var result = paginate.fromReactive(10, fetchListReactivePage);
            StepVerifier.create(result).expectNext("1", "2", "3", "4", "5", "6").verifyComplete();
        }

        @Test
        @DisplayName("Should return empty flux when there is no data and no total page count")
        void noDataNoTotalPagesReactive() {
            var paginate = new Paginate();
            when(fetchListReactivePage.apply(1L)).thenReturn(Mono.just(List.of()));

            var result = paginate.fromReactive(10, fetchListReactivePage);
            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("Should respect max pages limit reactively when there is no total page count")
        void limitPagesNoTotalPagesReactive() {
            var paginate = new Paginate();
            when(fetchListReactivePage.apply(1L)).thenReturn(Mono.just(List.of("1", "2", "3")));
            when(fetchListReactivePage.apply(2L)).thenReturn(Mono.just(List.of("4", "5", "6")));

            var result = paginate.fromReactive(2, fetchListReactivePage);
            StepVerifier.create(result).expectNext("1", "2", "3", "4", "5", "6").verifyComplete();
        }
    }
}
