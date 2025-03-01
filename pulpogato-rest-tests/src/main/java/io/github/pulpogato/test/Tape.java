package io.github.pulpogato.test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Tape implements Closeable {

    private final String fileName;
    @Getter private final List<Exchange> exchanges;

    public static Tape getTape(String tapeName) {
        var resourceName = "tapes/" + tapeName + ".yml";
        var fileName = "src/test/resources/" + resourceName;
        createDirectory(fileName);

        log.info("tapeName: {}, resourceName: {}, fileName: {}", tapeName, resourceName, fileName);

        List<Exchange> exchanges1;

        try (var stream = Tape.class.getResourceAsStream("/" + resourceName)) {
            if (stream != null) {
                log.info("Loading exchanges from tape: {}", resourceName);
                exchanges1 = new ObjectMapper(new YAMLFactory()).readValue(stream, new TypeReference<>() {
                });
            } else {
                log.info("No exchanges found in tape: {}", resourceName);
                exchanges1 = new ArrayList<>();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new Tape(fileName, new ArrayList<>(exchanges1));
    }

    private static void createDirectory(String fileName) {
        List<String> list = new ArrayList<>(Arrays.stream(fileName.split("/")).toList());
        log.info("list: {}", list);
        list.removeLast();
        String dirName = String.join("/", list);
        if (new File(dirName).mkdirs()) {
            log.info("Directory created: {}", dirName);
        }
    }

    @Override
    public void close() throws IOException {
        YAMLFactory yamlFactory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        var sw = new StringWriter();
        new ObjectMapper(yamlFactory).writeValue(sw, exchanges);
        if (!exchanges.isEmpty()) {
            try (FileWriter writer = new FileWriter(fileName)) {
                writer.write(sw.toString());
            }
        }
    }
}
