package io.github.pulpogato.test;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

@RestController
@Slf4j
public class ProxyController {
    private static final String DEFAULT_SERVER = "api.github.com";
    private static final int DEFAULT_PORT = 443;

    private final RestTemplate restTemplate;

    public ProxyController() {
        // Configure RestTemplate with Apache HttpClient to support the PATCH method
        var httpClient = HttpClients.createDefault();
        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @RequestMapping("/**")
    @SuppressWarnings("unused")
    public ResponseEntity<@NonNull String> mirrorRest(
            @RequestBody(required = false) String body, HttpMethod method, HttpServletRequest request)
            throws URISyntaxException, IOException {
        try (Tape tape = Tape.getTape(request.getHeader("TapeName"))) {
            Exchange.Request exchangeRequest = toExchangeRequest(body, request);

            var matchingExchanges = tape.getExchanges().stream()
                    .filter(it -> it.getRequest().equals(exchangeRequest))
                    .findFirst();
            if (matchingExchanges.isPresent()) {
                return getCachedResponse(tape, matchingExchanges.get());
            } else {
                log.info("Request: {}", Tape.OBJECT_MAPPER.writeValueAsString(exchangeRequest));
                return getLiveResponse(body, method, request, tape, exchangeRequest);
            }
        }
    }

    private ResponseEntity<@NonNull String> getLiveResponse(
            String body, HttpMethod method, HttpServletRequest request, Tape tape, Exchange.Request exchangeRequest)
            throws URISyntaxException {
        log.info("Fetching live data from server: {}", request.getRequestURI());

        HttpEntity<@NonNull String> entity = new HttpEntity<>(body, buildRequestHeaders(request));
        ResponseEntity<@NonNull String> exchange = getLiveResponse(method, buildUri(request), entity);
        Map<String, String> singleValueMap = getInterestingResponseHeaders(exchange);

        var response = Exchange.Response.builder()
                .statusCode(exchange.getStatusCode().value())
                .headers(singleValueMap)
                .body(prettifyBody(exchange.getBody()));

        tape.getExchanges()
                .add(Exchange.builder()
                        .request(exchangeRequest)
                        .response(response.build())
                        .build());
        return exchange;
    }

    private static ResponseEntity<@NonNull String> getCachedResponse(Tape tape, Exchange exchange) {
        log.info("Found exchange in tape: {}", tape);
        var headers = new HttpHeaders();
        exchange.getResponse().getHeaders().forEach(headers::add);
        return ResponseEntity.status(exchange.getResponse().getStatusCode())
                .headers(headers)
                .body(exchange.getResponse().getBody());
    }

    private static URI buildUri(HttpServletRequest request) throws URISyntaxException {
        String githubToken = System.getenv("GITHUB_TOKEN");
        String githubHost = Optional.ofNullable(System.getenv("GITHUB_HOST")).orElse(DEFAULT_SERVER);
        int githubPort = Integer.parseInt(
                Optional.ofNullable(System.getenv("GITHUB_PORT")).orElse(String.valueOf(DEFAULT_PORT)));
        if (githubToken == null) {
            throw new IllegalStateException("GITHUB_TOKEN is not set and no cached exchange found.");
        }
        var prefix = githubHost.equals(DEFAULT_SERVER) ? "" : "/api/v3";
        return new URI(
                "https",
                null,
                githubHost,
                githubPort,
                prefix + request.getRequestURI(),
                request.getQueryString(),
                null);
    }

    private ResponseEntity<@NonNull String> getLiveResponse(
            HttpMethod method, URI uri, HttpEntity<@NonNull String> entity) {
        try {
            return restTemplate.exchange(uri, method, entity, String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsString());
        }
    }

    private static HttpHeaders buildRequestHeaders(HttpServletRequest request) {
        var headers = new HttpHeaders();
        request.getHeaderNames().asIterator().forEachRemaining(k -> {
            String v = request.getHeader(k);
            if (!k.equalsIgnoreCase("user-agent") && !k.equalsIgnoreCase("host")) {
                headers.add(k, v);
            }
        });

        String githubToken = Optional.ofNullable(System.getenv("GITHUB_TOKEN"))
                .orElseThrow(() -> new IllegalStateException("GITHUB_TOKEN is not set and no cached exchange found."));
        headers.put("Authorization", List.of("Bearer " + githubToken));
        return headers;
    }

    private static Exchange.Request toExchangeRequest(String body, HttpServletRequest request) {
        var requestHeadersMap = new HashMap<String, String>();

        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
            Set<String> excludedHeaders = Set.of(
                    "user-agent", "host", "TapeName", "Content-Length", "X-GitHub-Api-Version", "X-Pulpogato-Version");
            if (excludedHeaders.stream().noneMatch(it -> it.equalsIgnoreCase(headerName))) {
                requestHeadersMap.put(headerName, request.getHeader(headerName));
            }
        });

        return Exchange.Request.builder()
                .method(request.getMethod())
                .uri(request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : ""))
                .protocol(request.getProtocol())
                .headers(requestHeadersMap)
                .body(prettifyBody(body))
                .build();
    }

    private static String prettifyBody(String body) {
        if (body != null) {
            try {
                ObjectMapper jsonMapper = new ObjectMapper();
                var exchangeBody = jsonMapper.readTree(body);
                return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exchangeBody);
            } catch (Exception e) {
                return body;
            }
        }
        return null;
    }

    private static Map<String, String> getInterestingResponseHeaders(ResponseEntity<@NonNull String> exchange) {
        Map<String, String> singleValueMap = new HashMap<>(exchange.getHeaders().toSingleValueMap());
        var removeHeaders = List.of(
                "Access-Control-.+",
                "Cache-Control",
                "Content-Length",
                "Content-Security-Policy",
                "Date",
                "ETag",
                "Last-Modified",
                "Referrer-Policy",
                "Server",
                "Set-Cookie",
                "Strict-Transport-Security",
                "Vary",
                "X-.+");
        new HashSet<>(singleValueMap.keySet()).forEach(key -> {
            if (removeHeaders.stream().anyMatch(regex -> key.toLowerCase().matches(regex.toLowerCase()))) {
                singleValueMap.remove(key);
            }
        });
        return singleValueMap;
    }
}
