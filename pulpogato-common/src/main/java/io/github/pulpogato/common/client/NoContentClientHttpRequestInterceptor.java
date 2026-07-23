package io.github.pulpogato.common.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * A {@link ClientHttpRequestInterceptor} that handles 204 No Content responses by replacing the
 * response body with an empty stream. This prevents Spring's {@link org.springframework.web.client.RestClient}
 * from attempting to deserialize an empty response body, which would otherwise fail when no
 * Content-Type header is present (defaulting to {@code application/octet-stream}).
 *
 * <p>This is the {@link org.springframework.web.client.RestClient} equivalent of
 * {@link NoContentExchangeFunction}.
 */
public class NoContentClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        if (response.getStatusCode().isSameCodeAs(HttpStatus.NO_CONTENT)) {
            return new EmptyBodyClientHttpResponse(response);
        }
        return response;
    }

    private static final class EmptyBodyClientHttpResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;

        private EmptyBodyClientHttpResponse(ClientHttpResponse delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }
}
