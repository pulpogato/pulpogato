package io.github.pulpogato.test;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final Path file;
    private final Path tapesDirectory;

    @Getter
    private final List<Exchange> exchanges;

    private final Mode mode;
    private final String originalContents;

    enum Mode {
        REPLAY,
        RECORD;

        static Mode configured() {
            var value = System.getProperty("pulpogato.tape.mode", "replay");
            return parse(value);
        }

        static Mode parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "replay" -> REPLAY;
                case "record" -> RECORD;
                default ->
                    throw new IllegalArgumentException(
                            "Invalid pulpogato.tape.mode: " + value + ". Expected replay or record.");
            };
        }
    }

    public static Tape getTape(String tapeName) {
        return getTape(tapeName, Mode.configured());
    }

    static Tape getTape(String tapeName, Mode mode) {
        return getTape(tapeName, Path.of("src/test/resources/tapes"), mode);
    }

    static Tape getTape(String tapeName, Path tapesDirectory, Mode mode) {
        if (tapeName == null || tapeName.isBlank()) {
            throw new IllegalArgumentException("TapeName must identify a tape.");
        }
        if (tapeName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid tape name: " + tapeName);
        }

        // tapeName reaches here via the ProxyController's TapeName header, so it must stay
        // confined to the tapes directory rather than escaping it via "../" segments.
        var tapesDir = tapesDirectory.toAbsolutePath().normalize();
        var file = tapesDir.resolve(tapeName + ".yml").normalize();
        if (!file.startsWith(tapesDir)) {
            throw new IllegalArgumentException("Invalid tape name: " + tapeName);
        }
        rejectSymbolicLinks(tapesDir, file);
        var resourceRoot = Path.of("/tapes");
        var resource = resourceRoot.resolve(tapesDir.relativize(file)).normalize();
        if (!resource.startsWith(resourceRoot)) {
            throw new IllegalArgumentException("Invalid tape resource: " + tapeName);
        }
        var resourceName = resource.toString().replace('\\', '/');

        List<Exchange> exchanges;

        // Read from the source file directly rather than the classpath: within a single test,
        // a tape can be written and re-read multiple times (e.g. when following a redirect),
        // but the classpath copy is only refreshed by processTestResources between runs.
        try (var stream = Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                ? Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)
                : Tape.class.getResourceAsStream(resourceName)) {
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

        return new Tape(
                file,
                tapesDir,
                new ArrayList<>(exchanges),
                mode,
                mode == Mode.RECORD ? serializeExchanges(exchanges) : "");
    }

    private static void rejectSymbolicLinks(Path tapesDirectory, Path file) {
        // Lexical containment alone would still allow a request header to select a symlink's external target.
        for (var current = file; current != null && current.startsWith(tapesDirectory); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("Tape path must not contain symbolic links: " + file);
            }
        }
    }

    boolean isRecording() {
        return mode == Mode.RECORD;
    }

    @Override
    public void close() throws IOException {
        if (!isRecording()) {
            return;
        }
        var contents = serializeExchanges(exchanges);
        if (contents.equals(originalContents)) {
            return;
        }
        rejectSymbolicLinks(tapesDirectory, file);
        Files.createDirectories(file.getParent());
        rejectSymbolicLinks(tapesDirectory, file);
        log.info("Saving {} exchanges to tape: {}", exchanges.size(), file);
        Files.writeString(
                file,
                contents,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
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
                .append("fileName", file)
                .append("exchanges", exchanges.size())
                .toString();
    }
}
