package io.github.pulpogato.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@Slf4j
public class ProxyController {
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final String server = "api.github.com";
    private static final int port = 443;

    @RequestMapping("/**")
    @SuppressWarnings("unused")
    public ResponseEntity<String> mirrorRest(@RequestBody(required = false) String body, HttpMethod method, HttpServletRequest request) throws URISyntaxException, IOException {
        try (Tape tape = Tape.getTape(request.getHeader("TapeName"))) {
            Exchange.Request exchangeRequest = toExchangeRequest(body, request);

            var matchingExchanges = tape.getExchanges().stream().filter(it -> it.getRequest().equals(exchangeRequest)).findFirst();
            if (matchingExchanges.isPresent()) {
                log.info("Found exchange in tape: {}", tape);
                var exchange = matchingExchanges.get();
                var headers = new HttpHeaders();
                exchange.getResponse().getHeaders().forEach(headers::add);
                return ResponseEntity
                        .status(exchange.getResponse().getStatusCode())
                        .headers(headers)
                        .body(exchange.getResponse().getBody());
            }

            log.info("Fetching live data from server: {}", request.getRequestURI());

            URI uri = new URI("https", null, server, port, request.getRequestURI(), request.getQueryString(), null);

            HttpEntity<String> entity = new HttpEntity<>(body, buildRequestHeaders(request));

            ResponseEntity<String> exchange = getLiveResponse(method, uri, entity);
            Map<String, String> singleValueMap = getInterestingResponseHeaders(exchange);
            var response = Exchange.Response.builder()
                    .statusCode(exchange.getStatusCode().value())
                    .headers(singleValueMap)
                    .body(prettifyBody(exchange.getBody()));

            tape.getExchanges().add(Exchange.builder().request(exchangeRequest).response(response.build()).build());
            return exchange;
        }
    }

    private static ResponseEntity<String> getLiveResponse(HttpMethod method, URI uri, HttpEntity<String> entity) {
        try {
            return restTemplate.exchange(uri, method, entity, String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity
                    .status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsString());
        }
    }

    private static HttpHeaders buildRequestHeaders(HttpServletRequest request) {
        var headers = new HttpHeaders();
        request.getHeaderNames().asIterator()
                .forEachRemaining(k -> {
                    String v = request.getHeader(k);
                    if (!k.equalsIgnoreCase("user-agent") && !k.equalsIgnoreCase("host")) {
                        headers.add(k, v);
                    }
                });

        /*
         * export GITHUB_TOKEN=$(gh auth token)
         */
        String githubToken = Optional.ofNullable(System.getenv("GITHUB_TOKEN"))
                .orElseThrow(() -> new IllegalStateException("GITHUB_TOKEN is not set and no cached exchange found."));
        headers.put("Authorization", List.of("Bearer " + githubToken));
        return headers;
    }

    private static Exchange.Request toExchangeRequest(String body, HttpServletRequest request) {
        var requestHeadersMap = new HashMap<String, String>();

        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
            if (Set.of("user-agent", "host", "TapeName").stream().noneMatch(it -> it.equalsIgnoreCase(headerName))) {
                requestHeadersMap.put(headerName, request.getHeader(headerName));
            }
        });

        return Exchange.Request.builder()
                .method(request.getMethod())
                .uri(request.getRequestURI())
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

    private static Map<String, String> getInterestingResponseHeaders(ResponseEntity<String> exchange) {
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
                "Strict-Transport-Security",
                "Vary",
                "X-.+"
        );
        new HashSet<>(singleValueMap.keySet()).forEach(key -> {
            if (removeHeaders.stream().anyMatch(regex -> key.toLowerCase().matches(regex.toLowerCase()))) {
                singleValueMap.remove(key);
            }
        });
        return singleValueMap;
    }

}
