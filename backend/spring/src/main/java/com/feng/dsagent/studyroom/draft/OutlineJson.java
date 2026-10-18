package com.feng.dsagent.studyroom.draft;

import com.feng.dsagent.common.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class OutlineJson {
    private final ObjectMapper mapper;

    public OutlineJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public <T> T read(String value, Class<T> type) {
        return mapper.readValue(value, type);
    }

    public Map<String, Object> object(String value) {
        return mapper.convertValue(
                mapper.readTree(value),
                mapper.getTypeFactory()
                        .constructMapType(LinkedHashMap.class, String.class, Object.class));
    }

    public Object value(String value) {
        return value == null ? null : mapper.readTree(value);
    }

    public static String id() {
        return UUID.randomUUID().toString();
    }

    public static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public static ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }

    public static void require(boolean valid, String code, String message) {
        if (!valid) throw error(HttpStatus.CONFLICT, code, message);
    }
}
