package io.github.pulpogato.common.client;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

final class RedirectPolicy {
    private RedirectPolicy() {}

    static boolean follows(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    static HttpMethod method(int status, HttpMethod original) {
        if ((status == 303 && original != HttpMethod.HEAD)
                || ((status == 301 || status == 302) && original == HttpMethod.POST)) {
            return HttpMethod.GET;
        }
        return original;
    }

    static URI resolve(URI request, URI location) {
        if (location.toString().startsWith("?")) {
            // URI.resolve drops the final path segment for a query-only reference.
            return URI.create(request.toString().split("[?#]", 2)[0] + location);
        }
        return request.resolve(location);
    }

    static boolean allows(URI request, URI target) {
        var scheme = target.getScheme();
        // Downstream filters may inject credentials again, so stripping headers alone cannot
        // make a cross-origin redirect safe for an arbitrary client filter chain.
        return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                && target.getHost() != null
                && target.getRawUserInfo() == null
                && scheme.equalsIgnoreCase(request.getScheme())
                && target.getHost().equalsIgnoreCase(request.getHost())
                && effectivePort(request) == effectivePort(target);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() != -1 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
    }

    static void removeBodyHeaders(HttpHeaders headers) {
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        headers.remove(HttpHeaders.CONTENT_TYPE);
        headers.remove(HttpHeaders.TRANSFER_ENCODING);
        headers.remove(HttpHeaders.CONTENT_ENCODING);
        headers.remove(HttpHeaders.CONTENT_LANGUAGE);
        headers.remove(HttpHeaders.CONTENT_LOCATION);
        headers.remove(HttpHeaders.CONTENT_RANGE);
        headers.remove("Content-MD5");
        headers.remove("Digest");
        headers.remove("Content-Digest");
        headers.remove("Repr-Digest");
        headers.remove("Trailer");
        headers.remove(HttpHeaders.EXPECT);
    }
}
