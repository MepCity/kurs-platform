package org.mepcity.kursplatform.org.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mepcity.kursplatform.org.application.BrandSettings;
import org.mepcity.kursplatform.org.application.OrganizationBrandResultSerializer;
import org.mepcity.kursplatform.org.infrastructure.OrganizationResultSerializationException;

/** Jackson-only implementation; the application layer never depends on ObjectMapper. */
public final class JacksonOrganizationBrandResultSerializer implements OrganizationBrandResultSerializer {
    private final ObjectMapper objectMapper;

    public JacksonOrganizationBrandResultSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override public String serialize(BrandSettings.Brand value) { return write(value); }
    @Override public String serialize(BrandSettings.Palette value) { return write(value); }
    @Override public String serialize(BrandSettings.Modules value) { return write(value); }
    @Override public BrandSettings.Brand deserializeBrand(String payload) { return read(payload, BrandSettings.Brand.class); }
    @Override public BrandSettings.Palette deserializePalette(String payload) { return read(payload, BrandSettings.Palette.class); }
    @Override public BrandSettings.Modules deserializeModules(String payload) { return read(payload, BrandSettings.Modules.class); }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new OrganizationResultSerializationException(exception); }
    }

    private <T> T read(String payload, Class<T> type) {
        if (payload == null) throw new OrganizationResultSerializationException(new IllegalArgumentException("Missing replay payload"));
        try { return objectMapper.readValue(payload, type); }
        catch (JsonProcessingException exception) { throw new OrganizationResultSerializationException(exception); }
    }
}
