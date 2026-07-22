package io.github.pulpogato.common.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.core.io.ClassPathResource;

/**
 * Loads the headers to add to every outgoing request from {@code pulpogato-headers.properties} on
 * the classpath, shared by {@link DefaultHeadersExchangeFunction} and
 * {@link DefaultHeadersClientHttpRequestInterceptor}. Returning a header-name-to-value map (rather
 * than named accessors) means adding a new header only requires a change here, not in either
 * wrapper.
 */
class PulpogatoHeadersLoader {

    private static final Map<String, String> PROPERTY_TO_HEADER = Map.of(
            "pulpogato.version", "X-Pulpogato-Version",
            "github.api.version", "X-GitHub-Api-Version");

    private PulpogatoHeadersLoader() {}

    static Map<String, String> loadHeaders() {
        var headers = new LinkedHashMap<String, String>();
        try (InputStream input = new ClassPathResource("pulpogato-headers.properties").getInputStream()) {
            var properties = new Properties();
            properties.load(input);
            PROPERTY_TO_HEADER.forEach((propertyKey, headerName) -> {
                var value = properties.getProperty(propertyKey);
                if (value != null) {
                    headers.put(headerName, value);
                }
            });
        } catch (IOException e) {
            // No headers file on the classpath; fall through with whatever was collected so far.
        }
        return headers;
    }
}
