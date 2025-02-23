package io.github.pulpogato.test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

@RestController
@Slf4j
public class ProxyController {
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final String server = "api.github.com";
    private static final int port = 443;

    @RequestMapping("/**")
    public ResponseEntity<String> mirrorRest(@RequestBody(required = false) String body, HttpMethod method, HttpServletRequest request) throws URISyntaxException, IOException {
        var pathName = request.getServletPath()
                .replaceAll("^/", "")
                .replaceAll("/$", "")
                .replace("?", "_");
        var resourceName = "tapes/" + pathName + ".yml";
        var fileName = "src/test/resources/" + resourceName;
        createDirectory(fileName);

        log.info("pathName: {}, resourceName: {}, fileName: {}", pathName, resourceName, fileName);

        var requestHeadersMap = new HashMap<String, String>();

        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
            if (Set.of("user-agent", "host").stream().noneMatch(it -> it.equalsIgnoreCase(headerName))) {
                requestHeadersMap.put(headerName, request.getHeader(headerName));
            }
        });

        var requestBuilder = Exchange.Request.builder()
                .method(request.getMethod())
                .uri(request.getRequestURI())
                .protocol(request.getProtocol())
                .headers(requestHeadersMap)
                .body(prettifyBody(body));

        Exchange.Request exchangeRequest = requestBuilder.build();

        var exchangeBuilder = Exchange.builder()
                .request(exchangeRequest);


        try (var stream = getClass().getResourceAsStream("/" + resourceName)) {
            if (stream != null) {
                log.info("Loading exchanges from tape: {}", resourceName);
                var exchanges = new ObjectMapper(new YAMLFactory()).readValue(stream, new TypeReference<List<Exchange>>() {
                });
                Optional<Exchange> first = exchanges.stream().filter(it -> it.getRequest().equals(exchangeRequest)).findFirst();
                if (first.isPresent()) {
                    log.info("Found exchange in tape: {}", resourceName);
                    var exchange = first.get();
                    var headers = new HttpHeaders();
                    exchange.getResponse().getHeaders().forEach(headers::add);
                    return ResponseEntity
                            .status(exchange.getResponse().getStatusCode())
                            .headers(headers)
                            .body(exchange.getResponse().getBody());
                }
            }
        }

        log.info("Fetching live data from server: {}", resourceName);

        URI uri = new URI("https", null, server, port, request.getRequestURI(), request.getQueryString(), null);

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

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try (FileWriter fileWriter = new FileWriter(fileName)) {
            ResponseEntity<String> exchange = restTemplate.exchange(uri, method, entity, String.class);

            Map<String, String> singleValueMap = getInterestingHeaders(exchange);

            var response2 = Exchange.Response.builder()
                    .statusCode(exchange.getStatusCode().value())
                    .headers(singleValueMap)
                    .body(prettifyBody(exchange.getBody()));

            var forList = exchangeBuilder
                    .response(response2.build());

            var toStore = List.of(forList.build());
            YAMLFactory yamlFactory = new YAMLFactory()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
            var sw = new StringWriter();
            new ObjectMapper(yamlFactory).writeValue(sw, toStore);
            fileWriter.write(sw.toString());

            return exchange;
        } catch (HttpClientErrorException ex) {
            return ResponseEntity
                    .status(ex.getStatusCode())
                    .headers(ex.getResponseHeaders())
                    .body(ex.getResponseBodyAsString());
        }
    }

    private static void createDirectory(String fileName) {
        List<String> list = new ArrayList<>(Arrays.stream(fileName.split("/")).toList());
        log.info("list: {}", list);
        list.removeLast();
        String dirName = String.join("/", list);
        new File(dirName).mkdirs();
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

    private static Map<String, String> getInterestingHeaders(ResponseEntity<String> exchange) {
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
