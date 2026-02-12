package io.github.pulpogato.githubfiles;

import java.util.stream.Stream;

final class Mappers {

    interface Mapper {
        <T> T readValue(String content, Class<T> valueType) throws Exception;

        String writeValueAsString(Object value) throws Exception;
    }

    record MapperPair(String name, Mapper yamlMapper, Mapper jsonMapper) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static Mapper wrap(com.fasterxml.jackson.databind.ObjectMapper om) {
        return new Mapper() {
            @Override
            public <T> T readValue(String content, Class<T> valueType) throws Exception {
                return om.readValue(content, valueType);
            }

            @Override
            public String writeValueAsString(Object value) throws Exception {
                return om.writeValueAsString(value);
            }
        };
    }

    private static Mapper wrap(tools.jackson.databind.ObjectMapper om) {
        return new Mapper() {
            @Override
            public <T> T readValue(String content, Class<T> valueType) {
                return om.readValue(content, valueType);
            }

            @Override
            public String writeValueAsString(Object value) {
                return om.writeValueAsString(value);
            }
        };
    }

    static Stream<MapperPair> mappers() {
        var j2Yaml = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        var j2Json = new com.fasterxml.jackson.databind.ObjectMapper();
        var j3Yaml = tools.jackson.dataformat.yaml.YAMLMapper.builder().build();
        var j3Json = tools.jackson.databind.json.JsonMapper.builder().build();

        return Stream.of(
                new MapperPair("Jackson2", wrap(j2Yaml), wrap(j2Json)),
                new MapperPair("Jackson3", wrap(j3Yaml), wrap(j3Json)));
    }

    private Mappers() {}
}
