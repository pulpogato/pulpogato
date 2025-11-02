package io.github.pulpogato.test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.commons.util.ToStringBuilder;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Tape implements Closeable {

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
        YAMLFactory yamlFactory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        var sw = new StringWriter();
        new ObjectMapper(yamlFactory).writeValue(sw, exchanges);
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(sw.toString());
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
