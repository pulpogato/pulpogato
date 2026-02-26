package io.github.pulpogato.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class NoContentExchangeFunctionTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private final NoContentExchangeFunction filter = new NoContentExchangeFunction();

    @Test
    void replacesBodyWithEmptyFluxFor204Response() {
        var noContentResponse = ClientResponse.create(HttpStatus.NO_CONTENT)
                .body("should be removed")
                .build();

        given(exchangeFunction.exchange(any(ClientRequest.class))).willReturn(Mono.just(noContentResponse));

        var request = ClientRequest.create(HttpMethod.POST, URI.create("https://api.github.com/test"))
                .build();

        StepVerifier.create(filter.filter(request, exchangeFunction))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                    StepVerifier.create(response.bodyToMono(String.class)).verifyComplete();
                })
                .verifyComplete();
    }

    @Test
    void passesNon204ResponseThrough() {
        var okResponse = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"key\": \"value\"}")
                .build();

        given(exchangeFunction.exchange(any(ClientRequest.class))).willReturn(Mono.just(okResponse));

        var request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.github.com/test"))
                .build();

        StepVerifier.create(filter.filter(request, exchangeFunction))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                    StepVerifier.create(response.bodyToMono(String.class))
                            .expectNext("{\"key\": \"value\"}")
                            .verifyComplete();
                })
                .verifyComplete();
    }
}
