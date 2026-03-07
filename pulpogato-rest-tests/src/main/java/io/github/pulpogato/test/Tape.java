package io.github.pulpogato.test;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.commons.util.ToStringBuilder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Tape implements Closeable {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(YAMLFactory.builder()
            .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
            .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
            .build());
    private final String fileName;

    @Getter
    private final List<Exchange> exchanges;

    public static Tape getTape(String tapeName) {
        var resourceName = "tapes/" + tapeName + ".yml";
        var fileName = "src/test/resources/" + resourceName;
        createDirectory(fileName);

        List<Exchange> exchanges;

        try (var stream = Tape.class.getResourceAsStream("/" + resourceName)) {
            if (stream != null) {
                exchanges = new ObjectMapper(new YAMLFactory()).readValue(stream, new TypeReference<>() {});
                log.info("Loaded {} exchanges from tape: {}", exchanges.size(), resourceName);
            } else {
                log.warn("No tape found: {}", resourceName);
                exchanges = new ArrayList<>();
            }
        } catch (IOException e) {
            log.error("Failed to load tape: {}", resourceName, e);
            throw new TapeLoadingException(e);
        }

        return new Tape(fileName, new ArrayList<>(exchanges));
    }

    private static void createDirectory(String fileName) {
        List<String> list = new ArrayList<>(Arrays.stream(fileName.split("/")).toList());
        list.removeLast();
        String dirName = String.join("/", list);
        if (new File(dirName).mkdirs()) {
            log.info("Directory created: {}", dirName);
        }
    }

    @Override
    public void close() throws IOException {
        if (exchanges.isEmpty()) {
            log.info("No exchanges to save: {}", fileName);
            return;
        }
        log.info("Saving {} exchanges to tape: {}", exchanges.size(), fileName);
        var string = serializeExchanges(exchanges);
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(string);
        }
    }

    /**
     * Serializes exchanges to YAML, omitting null values so fields like bodyIsBinary do not appear
     * when null. Used by {@link #close()} and by tests.
     */
    public static String serializeExchanges(List<Exchange> exchanges) {
        JsonNode tree = OBJECT_MAPPER.valueToTree(exchanges);
        removeNullValues(tree);
        return OBJECT_MAPPER.writeValueAsString(tree);
    }

    /** Removes object properties whose value is null so they are omitted from the written YAML. */
    private static void removeNullValues(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            new ArrayList<>(obj.propertyNames()).forEach(name -> {
                JsonNode value = obj.get(name);
                if (value.isNull()) {
                    obj.remove(name);
                } else {
                    removeNullValues(value);
                }
            });
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                removeNullValues(arr.get(i));
            }
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("fileName", fileName)
                .append("exchanges", exchanges.size())
                .toString();
    }
}
