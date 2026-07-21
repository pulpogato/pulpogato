package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.LongFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private static ResponseEntity<List<String>> withLink(List<String> items, String link) {
        var headers = new HttpHeaders();
        if (link != null) {
            headers.add("Link", link);
        }
        return new ResponseEntity<>(items, headers, HttpStatus.OK);
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
    @DisplayName("from(maxPages, fetchPage, hasNextPage)")
    class ListOnlyBasedWithHasNextPage {

        @Mock
        private LongFunction<List<String>> fetchListPage;

        private static final BiPredicate<Long, List<String>> PAGE_SIZE_IS_THREE = (page, items) -> items.size() == 3;

        @Test
        @DisplayName("Should stop after a short page without an extra request")
        void shortPageStopsWithoutExtraRequest() {
            var paginate = new Paginate();
            when(fetchListPage.apply(1L)).thenReturn(List.of("1", "2"));

            var result = paginate.from(10, fetchListPage, PAGE_SIZE_IS_THREE);
            assertThat(result).containsExactly("1", "2");
            verify(fetchListPage, never()).apply(2L);
        }

        @Test
        @DisplayName("Should concatenate items from multiple full pages until a short page is seen")
        void multipleFullPagesThenShortPage() {
            var paginate = new Paginate();
            when(fetchListPage.apply(1L)).thenReturn(List.of("1", "2", "3"));
            when(fetchListPage.apply(2L)).thenReturn(List.of("4", "5", "6"));
            when(fetchListPage.apply(3L)).thenReturn(List.of("7"));

            var result = paginate.from(10, fetchListPage, PAGE_SIZE_IS_THREE);
            assertThat(result).containsExactly("1", "2", "3", "4", "5", "6", "7");
        }

        @Test
        @DisplayName("Should fetch an extra page after a page for which hasNextPage returns true")
        void exactlyFullLastPageStillProbesOnce() {
            var paginate = new Paginate();
            when(fetchListPage.apply(1L)).thenReturn(List.of("1", "2", "3"));
            when(fetchListPage.apply(2L)).thenReturn(List.of());

            var result = paginate.from(10, fetchListPage, PAGE_SIZE_IS_THREE);
            assertThat(result).containsExactly("1", "2", "3");
            verify(fetchListPage).apply(2L);
        }

        @Test
        @DisplayName("Should return empty stream when there is no data")
        void noData() {
            var paginate = new Paginate();
            when(fetchListPage.apply(1L)).thenReturn(List.of());

            var result = paginate.from(10, fetchListPage, PAGE_SIZE_IS_THREE);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should respect max pages limit even when hasNextPage keeps returning true")
        void limitPages() {
            var paginate = new Paginate();
            when(fetchListPage.apply(1L)).thenReturn(List.of("1", "2", "3"));
            when(fetchListPage.apply(2L)).thenReturn(List.of("4", "5", "6"));

            var result = paginate.from(2, fetchListPage, PAGE_SIZE_IS_THREE);
            assertThat(result).containsExactly("1", "2", "3", "4", "5", "6");
        }
    }

    @Nested
    @DisplayName("from(maxPages, fetchPage, extractItems, hasNextPage)")
    class GenericBasedWithHasNextPage {

        @Mock
        private LongFunction<Response> fetchPage;

        private static final BiPredicate<Long, Response> MORE_PAGES_REPORTED =
                (page, response) -> page < response.totalPages();

        @Test
        @DisplayName("Should stop as soon as hasNextPage reports no more pages")
        void stopsWhenHasNextPageIsFalse() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(new Response(List.of("1", "2", "3"), 2));
            when(fetchPage.apply(2L)).thenReturn(new Response(List.of("4", "5"), 2));

            var result = paginate.from(10, fetchPage, PaginateTest::getStream, MORE_PAGES_REPORTED);
            assertThat(result).containsExactly("1", "2", "3", "4", "5");
        }

        @Test
        @DisplayName("Should not fetch a next page when hasNextPage is false on the first page")
        void singlePageWhenHasNextPageIsFalseImmediately() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(new Response(List.of("1", "2", "3"), 1));

            var result = paginate.from(10, fetchPage, PaginateTest::getStream, MORE_PAGES_REPORTED);
            assertThat(result).containsExactly("1", "2", "3");
        }

        @Test
        @DisplayName("Should respect max pages limit even when hasNextPage keeps returning true")
        void limitPages() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(new Response(List.of("1", "2", "3"), 5));
            when(fetchPage.apply(2L)).thenReturn(new Response(List.of("4", "5", "6"), 5));

            var result = paginate.from(2, fetchPage, PaginateTest::getStream, MORE_PAGES_REPORTED);
            assertThat(result).containsExactly("1", "2", "3", "4", "5", "6");
        }
    }

    @Nested
    @DisplayName("fromLinkHeader(maxPages, fetchPage)")
    class LinkHeaderBased {

        @Mock
        private LongFunction<ResponseEntity<List<String>>> fetchPage;

        @Test
        @DisplayName("Should stop as soon as the Link header stops reporting rel=\"next\"")
        void stopsWhenLinkHeaderHasNoNextPage() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(withLink(List.of("1", "2", "3"), "<url>; rel=\"next\""));
            when(fetchPage.apply(2L)).thenReturn(withLink(List.of("4", "5"), "<url>; rel=\"last\""));

            var result = paginate.fromLinkHeader(10, fetchPage);
            assertThat(result).containsExactly("1", "2", "3", "4", "5");
            verify(fetchPage, never()).apply(3L);
        }

        @Test
        @DisplayName("Should recognize an unquoted rel=next link-param per RFC 8288")
        void unquotedRelNextIsRecognized() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(withLink(List.of("1", "2", "3"), "<url>; rel=next"));
            when(fetchPage.apply(2L)).thenReturn(withLink(List.of("4", "5"), "<url>; rel=last"));

            var result = paginate.fromLinkHeader(10, fetchPage);
            assertThat(result).containsExactly("1", "2", "3", "4", "5");
        }

        @Test
        @DisplayName("Should not fetch a next page when the first page has no Link header")
        void singlePageWithNoLinkHeader() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(withLink(List.of("1", "2", "3"), null));

            var result = paginate.fromLinkHeader(10, fetchPage);
            assertThat(result).containsExactly("1", "2", "3");
            verify(fetchPage, never()).apply(2L);
        }

        @Test
        @DisplayName("Should treat a null response body as an empty page")
        void nullBodyIsTreatedAsEmpty() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(withLink(null, null));

            var result = paginate.fromLinkHeader(10, fetchPage);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should respect max pages limit even when the Link header keeps reporting rel=\"next\"")
        void limitPages() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(withLink(List.of("1", "2", "3"), "<url>; rel=\"next\""));
            when(fetchPage.apply(2L)).thenReturn(withLink(List.of("4", "5", "6"), "<url>; rel=\"next\""));

            var result = paginate.fromLinkHeader(2, fetchPage);
            assertThat(result).containsExactly("1", "2", "3", "4", "5", "6");
        }
    }

    @Nested
    @DisplayName("fromLinkHeaderReactive(maxPages, fetchPage)")
    class LinkHeaderBasedReactive {

        @Mock
        private LongFunction<Mono<ResponseEntity<List<String>>>> fetchPage;

        @Test
        @DisplayName("Should stop reactively as soon as the Link header stops reporting rel=\"next\"")
        void stopsWhenLinkHeaderHasNoNextPageReactive() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(Mono.just(withLink(List.of("1", "2", "3"), "<url>; rel=\"next\"")));
            when(fetchPage.apply(2L)).thenReturn(Mono.just(withLink(List.of("4", "5"), "<url>; rel=\"last\"")));

            var result = paginate.fromLinkHeaderReactive(10, fetchPage);
            StepVerifier.create(result).expectNext("1", "2", "3", "4", "5").verifyComplete();
            verify(fetchPage, never()).apply(3L);
        }

        @Test
        @DisplayName("Should not fetch a next page reactively when the first page has no Link header")
        void singlePageWithNoLinkHeaderReactive() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(Mono.just(withLink(List.of("1", "2", "3"), null)));

            var result = paginate.fromLinkHeaderReactive(10, fetchPage);
            StepVerifier.create(result).expectNext("1", "2", "3").verifyComplete();
            verify(fetchPage, never()).apply(2L);
        }

        @Test
        @DisplayName("Should treat a null response body as an empty page reactively")
        void nullBodyIsTreatedAsEmptyReactive() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(Mono.just(withLink(null, null)));

            var result = paginate.fromLinkHeaderReactive(10, fetchPage);
            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("Should respect max pages limit reactively even when the Link header keeps reporting rel=\"next\"")
        void limitPagesReactive() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(Mono.just(withLink(List.of("1", "2", "3"), "<url>; rel=\"next\"")));
            when(fetchPage.apply(2L)).thenReturn(Mono.just(withLink(List.of("4", "5", "6"), "<url>; rel=\"next\"")));

            var result = paginate.fromLinkHeaderReactive(2, fetchPage);
            StepVerifier.create(result).expectNext("1", "2", "3", "4", "5", "6").verifyComplete();
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

    @Nested
    @DisplayName("fromReactive(maxPages, fetchPage, hasNextPage)")
    class ListOnlyBasedWithHasNextPageReactive {

        @Mock
        private LongFunction<Mono<List<String>>> fetchListReactivePage;

        private static final BiPredicate<Long, List<String>> PAGE_SIZE_IS_THREE = (page, items) -> items.size() == 3;

        @Test
        @DisplayName("Should stop after a short page reactively without an extra request")
        void shortPageStopsWithoutExtraRequestReactive() {
            var paginate = new Paginate();
            when(fetchListReactivePage.apply(1L)).thenReturn(Mono.just(List.of("1", "2")));

            var result = paginate.fromReactive(10, fetchListReactivePage, PAGE_SIZE_IS_THREE);
            StepVerifier.create(result).expectNext("1", "2").verifyComplete();
            verify(fetchListReactivePage, never()).apply(2L);
        }

        @Test
        @DisplayName("Should concatenate items from multiple full pages reactively until a short page is seen")
        void multipleFullPagesThenShortPageReactive() {
            var paginate = new Paginate();
            when(fetchListReactivePage.apply(1L)).thenReturn(Mono.just(List.of("1", "2", "3")));
            when(fetchListReactivePage.apply(2L)).thenReturn(Mono.just(List.of("4", "5", "6")));
            when(fetchListReactivePage.apply(3L)).thenReturn(Mono.just(List.of("7")));

            var result = paginate.fromReactive(10, fetchListReactivePage, PAGE_SIZE_IS_THREE);
            StepVerifier.create(result)
                    .expectNext("1", "2", "3", "4", "5", "6", "7")
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty flux when there is no data")
        void noDataReactive() {
            var paginate = new Paginate();
            when(fetchListReactivePage.apply(1L)).thenReturn(Mono.just(List.of()));

            var result = paginate.fromReactive(10, fetchListReactivePage, PAGE_SIZE_IS_THREE);
            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("Should respect max pages limit reactively even when hasNextPage keeps returning true")
        void limitPagesReactive() {
            var paginate = new Paginate();
            when(fetchListReactivePage.apply(1L)).thenReturn(Mono.just(List.of("1", "2", "3")));
            when(fetchListReactivePage.apply(2L)).thenReturn(Mono.just(List.of("4", "5", "6")));

            var result = paginate.fromReactive(2, fetchListReactivePage, PAGE_SIZE_IS_THREE);
            StepVerifier.create(result).expectNext("1", "2", "3", "4", "5", "6").verifyComplete();
        }
    }

    @Nested
    @DisplayName("fromReactive(maxPages, fetchPage, extractItems, hasNextPage)")
    class GenericBasedWithHasNextPageReactive {

        @Mock
        private LongFunction<Mono<Response>> fetchPage;

        private static final BiPredicate<Long, Response> MORE_PAGES_REPORTED =
                (page, response) -> page < response.totalPages();

        @Test
        @DisplayName("Should stop reactively as soon as hasNextPage reports no more pages")
        void stopsWhenHasNextPageIsFalseReactive() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(Mono.just(new Response(List.of("1", "2", "3"), 2)));
            when(fetchPage.apply(2L)).thenReturn(Mono.just(new Response(List.of("4", "5"), 2)));

            var result = paginate.fromReactive(10, fetchPage, PaginateTest::getFlux, MORE_PAGES_REPORTED);
            StepVerifier.create(result).expectNext("1", "2", "3", "4", "5").verifyComplete();
        }

        @Test
        @DisplayName("Should not fetch a next page reactively when hasNextPage is false on the first page")
        void singlePageWhenHasNextPageIsFalseImmediatelyReactive() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(Mono.just(new Response(List.of("1", "2", "3"), 1)));

            var result = paginate.fromReactive(10, fetchPage, PaginateTest::getFlux, MORE_PAGES_REPORTED);
            StepVerifier.create(result).expectNext("1", "2", "3").verifyComplete();
        }

        @Test
        @DisplayName("Should respect max pages limit reactively even when hasNextPage keeps returning true")
        void limitPagesReactive() {
            var paginate = new Paginate();
            when(fetchPage.apply(1L)).thenReturn(Mono.just(new Response(List.of("1", "2", "3"), 5)));
            when(fetchPage.apply(2L)).thenReturn(Mono.just(new Response(List.of("4", "5", "6"), 5)));

            var result = paginate.fromReactive(2, fetchPage, PaginateTest::getFlux, MORE_PAGES_REPORTED);
            StepVerifier.create(result).expectNext("1", "2", "3", "4", "5", "6").verifyComplete();
        }
    }
}
