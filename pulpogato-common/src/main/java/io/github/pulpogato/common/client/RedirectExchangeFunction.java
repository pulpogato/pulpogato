package io.github.pulpogato.common.client;

import java.net.URI;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * An {@link ExchangeFilterFunction} that transparently follows 3xx redirects, such as the ones
 * GitHub returns when a repository has been renamed. Without this, callers would see the raw
 * redirect response instead of the resource at its new location.
 */
public class RedirectExchangeFunction implements ExchangeFilterFunction {

    private static final int MAX_REDIRECTS = 5;

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return exchange(request, next, MAX_REDIRECTS);
    }

    private static Mono<ClientResponse> exchange(ClientRequest request, ExchangeFunction next, int remainingRedirects) {
        return next.exchange(request).flatMap(response -> {
            var status = response.statusCode().value();
            URI location = response.headers().asHttpHeaders().getLocation();
            if (remainingRedirects > 0 && RedirectPolicy.follows(status) && location != null) {
                var method = RedirectPolicy.method(status, request.method());
                var builder = ClientRequest.from(request)
                        .url(RedirectPolicy.resolve(request.url(), location))
                        .method(method);
                if (method != request.method()) {
                    builder.body(BodyInserters.empty()).headers(RedirectPolicy::removeBodyHeaders);
                }
                var redirected = builder.build();
                return response.releaseBody()
                        .then(Mono.defer(() -> exchange(redirected, next, remainingRedirects - 1)));
            }
            return Mono.just(response);
        });
    }
}
