package com.feng.dsagent.studyroom.fragment.support;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** A single server-side schema shared by generation and offline replay. */
@Component
public final class FragmentOutputContract {
    private final ObjectMapper mapper;
    private final Schema schema;

    public FragmentOutputContract(ObjectMapper mapper) {
        this.mapper = mapper;
        schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(SchemaLocation.of("classpath:studyroom/fragment-output.schema.json"));
    }

    public JsonNode parse(String raw) {
        if (raw == null || raw.length() > 64000) throw new IllegalArgumentException("FRAGMENT_JSON_INVALID");
        String cleaned = raw.strip();
        if (cleaned.startsWith("```json\n") && cleaned.endsWith("```"))
            cleaned = cleaned.substring(8, cleaned.length() - 3).strip();
        try {
            var errors = schema.validate(cleaned, InputFormat.JSON);
            if (!errors.isEmpty()) throw new IllegalArgumentException("FRAGMENT_SCHEMA_INVALID: " + errors.getFirst());
            return mapper.readTree(cleaned);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("FRAGMENT_JSON_INVALID", error);
        }
    }
}
