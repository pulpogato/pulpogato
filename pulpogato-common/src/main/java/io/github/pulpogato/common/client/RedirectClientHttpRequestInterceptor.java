package io.github.pulpogato.common.client;

import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;

/**
 * A {@link ClientHttpRequestInterceptor} that transparently follows 3xx redirects, such as the
 * ones GitHub returns when a repository has been renamed. Without this, callers would see the
 * raw redirect response instead of the resource at its new location.
 *
 * <p>This is the {@link org.springframework.web.client.RestClient} equivalent of
 * {@link RedirectExchangeFunction}.
 */
public class RedirectClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final int MAX_REDIRECTS = 5;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        return execute(request, body, execution, MAX_REDIRECTS);
    }

    private static ClientHttpResponse execute(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution, int remainingRedirects)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        URI location = response.getHeaders().getLocation();
        if (remainingRedirects > 0 && response.getStatusCode().is3xxRedirection() && location != null) {
            response.close();
            HttpRequest redirected = new HttpRequestWrapper(request) {
                @Override
                public URI getURI() {
                    return request.getURI().resolve(location);
                }
            };
            return execute(redirected, body, execution, remainingRedirects - 1);
        }
        return response;
    }
}
