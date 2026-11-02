package io.github.pulpogato.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;

class TapeModeTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @CsvSource({
        "REPLAY,false,false", "REPLAY,false,true", "REPLAY,true,false", "REPLAY,true,true",
        "RECORD,false,false", "RECORD,false,true", "RECORD,true,false", "RECORD,true,true"
    })
    void writesOnlyChangedRecordings(Tape.Mode mode, boolean exists, boolean changed) throws Exception {
        var tapes = directory.resolve("tapes");
        var file = tapes.resolve("suite/test.yml");
        var original = "# Keep existing formatting\n" + Tape.serializeExchanges(List.of(exchange("/existing")));
        var modifiedAt = FileTime.from(Instant.parse("2024-01-01T00:00:00Z"));
        if (exists) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, original, StandardCharsets.UTF_8);
            Files.setLastModifiedTime(file, modifiedAt);
        }

        try (var tape = Tape.getTape("suite/test", tapes, mode)) {
            assertThat(tape.isRecording()).isEqualTo(mode == Tape.Mode.RECORD);
            assertThat(tape.getExchanges()).hasSize(exists ? 1 : 0);
            if (changed) {
                tape.getExchanges().add(exchange("/café"));
            }
        }

        if (mode == Tape.Mode.RECORD && changed) {
            assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("/café");
            try (var replay = Tape.getTape("suite/test", tapes, Tape.Mode.REPLAY)) {
                assertThat(replay.getExchanges()).hasSize(exists ? 2 : 1);
            }
        } else if (exists) {
            assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(original);
            assertThat(Files.getLastModifiedTime(file)).isEqualTo(modifiedAt);
        } else {
            assertThat(tapes).doesNotExist();
        }
    }

    @Test
    void recordsRemovalOfAllExchanges() throws Exception {
        var file = directory.resolve("test.yml");
        Files.writeString(file, Tape.serializeExchanges(List.of(exchange("/existing"))));
        try (var tape = Tape.getTape("test", directory, Tape.Mode.RECORD)) {
            tape.getExchanges().clear();
        }
        try (var replay = Tape.getTape("test", directory, Tape.Mode.REPLAY)) {
            assertThat(replay.getExchanges()).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(Tape.Mode.class)
    void readsTheNormalizedPathWithoutCreatingIntermediateDirectories(Tape.Mode mode) throws Exception {
        Files.writeString(directory.resolve("test.yml"), Tape.serializeExchanges(List.of(exchange("/existing"))));

        try (var tape = Tape.getTape("missing/../test", directory, mode)) {
            assertThat(tape.getExchanges())
                    .singleElement()
                    .satisfies(exchange ->
                            assertThat(exchange.getRequest().getUri()).isEqualTo("/existing"));
        }

        assertThat(directory.resolve("missing")).doesNotExist();
    }

    @ParameterizedTest
    @CsvSource({
        "REPLAY,classpath/example", "RECORD,classpath/example",
        "REPLAY,classpath/missing/../example", "RECORD,classpath/missing/../example"
    })
    void loadsTheNormalizedClasspathResourceWithoutWritingItToSource(Tape.Mode mode, String tapeName) throws Exception {
        try (var tape = Tape.getTape(tapeName, directory, mode)) {
            assertThat(tape.getExchanges())
                    .singleElement()
                    .satisfies(exchange ->
                            assertThat(exchange.getRequest().getUri()).isEqualTo("/classpath"));
        }

        assertThat(directory.resolve("classpath")).doesNotExist();
    }

    @ParameterizedTest
    @CsvSource({"REPLAY,false", "RECORD,false", "REPLAY,true", "RECORD,true"})
    void rejectsSymbolicLinksToExternalFilesAndDirectories(Tape.Mode mode, boolean linkDirectory) throws Exception {
        var tapes = Files.createDirectory(directory.resolve("tapes"));
        var outside = Files.createDirectory(directory.resolve("outside"));
        var original = Tape.serializeExchanges(List.of(exchange("/external")));
        Files.writeString(outside.resolve("test.yml"), original);
        var tapeName = linkDirectory ? "suite/test" : "test";
        Files.createSymbolicLink(
                tapes.resolve(linkDirectory ? "suite" : "test.yml"),
                linkDirectory ? outside : outside.resolve("test.yml"));

        assertThatThrownBy(() -> Tape.getTape(tapeName, tapes, mode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbolic links");
        assertThat(Files.readString(outside.resolve("test.yml"))).isEqualTo(original);
    }

    @Test
    void rechecksSymbolicLinksBeforeWritingAChangedRecording() throws Exception {
        var tapes = Files.createDirectory(directory.resolve("tapes"));
        var outside = Files.createDirectory(directory.resolve("outside"));
        var tape = Tape.getTape("suite/test", tapes, Tape.Mode.RECORD);
        tape.getExchanges().add(exchange("/new"));
        Files.createSymbolicLink(tapes.resolve("suite"), outside);

        assertThatThrownBy(tape::close)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbolic links");
        assertThat(outside.resolve("test.yml")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escape", "/outside/tape"})
    void rejectsTapeNamesOutsideTheTapeDirectory(String name) {
        assertThatThrownBy(() -> Tape.getTape(name, directory, Tape.Mode.REPLAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tape name");
    }

    @ParameterizedTest
    @CsvSource({
        "REPLAY,..\\..\\outside", "RECORD,..\\..\\outside",
        "REPLAY,classpath\\..\\..\\outside", "RECORD,classpath\\..\\..\\outside"
    })
    void rejectsBackslashesBeforeClasspathFallback(Tape.Mode mode, String name) {
        assertThatThrownBy(() -> Tape.getTape(name, directory, mode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tape name");
        assertThat(directory.resolve("classpath")).doesNotExist();
    }

    @ParameterizedTest
    @CsvSource({"replay,REPLAY", "record,RECORD", "RECORD,RECORD"})
    void parsesExplicitModes(String value, Tape.Mode expected) {
        assertThat(Tape.Mode.parse(value)).isEqualTo(expected);
    }

    @Test
    void rejectsUnknownModes() {
        assertThatThrownBy(() -> Tape.Mode.parse("recrod"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected replay or record");
    }

    @Test
    void replayMissExplainsHowToRecordWithoutContactingGitHubOrCreatingDirectories() {
        var tapeName = "__pulpogato_replay_mode_test__/missing";
        var request = new MockHttpServletRequest("GET", "/repos/example/missing");
        request.addHeader("TapeName", tapeName);

        var controller = new ProxyController(Tape.Mode.REPLAY);
        assertThatThrownBy(() -> controller.mirrorRest(null, HttpMethod.GET, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(tapeName, "GET", "/repos/example/missing", "-PtapeMode=record");
        assertThat(Path.of("src/test/resources/tapes", tapeName).getParent()).doesNotExist();
    }

    private static Exchange exchange(String uri) {
        return Exchange.builder()
                .request(Exchange.Request.builder()
                        .method("GET")
                        .uri(uri)
                        .protocol("HTTP/1.1")
                        .headers(Map.of())
                        .build())
                .response(Exchange.Response.builder()
                        .statusCode(200)
                        .headers(Map.of())
                        .body("{}")
                        .build())
                .build();
    }
}
