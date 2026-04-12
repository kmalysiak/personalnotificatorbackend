package pl.kmalysiak.notificator.utils;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public final class CustomObjectMapper {

    private static final ObjectMapper MAPPER = create();

    private CustomObjectMapper() {}

    private static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();

        // Support java.time (Instant, ZonedDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());

        // Java time support
        mapper.registerModule(new JavaTimeModule());

        // Global override for LocalDateTime
        SimpleModule module = new SimpleModule();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);


        module.addDeserializer(LocalDateTime.class,
                new JsonDeserializer<>() {
                    @Override
                    public LocalDateTime deserialize(com.fasterxml.jackson.core.JsonParser p, DeserializationContext ctxt) throws java.io.IOException {
                        String value = p.getText();
                        // Accept BOTH:
                        // 2026-01-10T16:38:03.312727
                        // 2026-01-10T16:38:03.312727+00:00
                        if (value.endsWith("Z") || value.contains("+")) {
                            return OffsetDateTime.parse(value).atZoneSameInstant(TimeZoneUtils.TIME_Z_WARSAW).toLocalDateTime();
                        }
                        return LocalDateTime.parse(value);
                    }
                }
        );

        mapper.registerModule(module);


        // Ignore unknown fields (important for evolving JSON)
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }

    @SneakyThrows
     public static <T> T fromJson(String str, Class<T> clz){
         return get().readValue(str, clz);
    }

    @SneakyThrows
    public static <T> T mapObject(Object obj, Class<T> clz){
        String str = MAPPER.writeValueAsString(obj);
        return MAPPER.readValue(str, clz);
    }

    public static ObjectMapper get() {
        return MAPPER;
    }

    @SneakyThrows
    public static JsonNode readTree(String payload) {
        return MAPPER.readTree(payload);
    }

    @SneakyThrows
    public static String writeValueAsString(Object payload) {
        return MAPPER.writeValueAsString(payload);
    }
}
