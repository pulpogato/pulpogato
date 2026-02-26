package io.github.pulpogato.common.client;

import org.jspecify.annotations.NonNull;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * An {@link ExchangeFilterFunction} that handles 204 No Content responses by replacing the
 * response body with an empty flux. This prevents Spring's WebClient from attempting to
 * deserialize an empty response body, which would otherwise fail when no Content-Type
 * header is present (defaulting to {@code application/octet-stream}).
 */
public class NoContentExchangeFunction implements ExchangeFilterFunction {

    @Override
    @NonNull
    public Mono<ClientResponse> filter(@NonNull ClientRequest request, @NonNull ExchangeFunction next) {
        return next.exchange(request).map(response -> {
            if (response.statusCode().isSameCodeAs(HttpStatus.NO_CONTENT)) {
                return response.mutate().body(Flux.<DataBuffer>empty()).build();
            }
            return response;
        });
    }
}
