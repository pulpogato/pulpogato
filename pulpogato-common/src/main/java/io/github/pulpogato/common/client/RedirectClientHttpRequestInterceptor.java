package io.github.pulpogato.common.client;

import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * <p>Only redirects within the same origin (scheme, host, and effective port) are followed.
 * Cross-origin redirects, non-HTTP destinations, and destinations containing user information
 * are returned without following them so authentication filters cannot forward credentials to another origin.
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
        var response = execution.execute(request, body);
        var status = response.getStatusCode().value();
        URI location = response.getHeaders().getLocation();
        if (remainingRedirects > 0 && RedirectPolicy.follows(status) && location != null) {
            var target = RedirectPolicy.resolve(request.getURI(), location);
            if (!RedirectPolicy.allows(request.getURI(), target)) {
                return response;
            }
            var method = RedirectPolicy.method(status, request.getMethod());
            var headers = new HttpHeaders();
            headers.putAll(request.getHeaders());
            var redirectedBody = method == request.getMethod() ? body : new byte[0];
            if (method != request.getMethod()) {
                RedirectPolicy.removeBodyHeaders(headers);
            }
            response.close();
            var redirected = new HttpRequestWrapper(request) {
                @Override
                public URI getURI() {
                    return target;
                }

                @Override
                public HttpMethod getMethod() {
                    return method;
                }

                @Override
                public HttpHeaders getHeaders() {
                    return headers;
                }
            };
            return execute(redirected, redirectedBody, execution, remainingRedirects - 1);
        }
        return response;
    }
}
