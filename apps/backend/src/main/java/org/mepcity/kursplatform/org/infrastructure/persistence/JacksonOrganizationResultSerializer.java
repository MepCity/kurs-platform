package org.mepcity.kursplatform.org.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.mepcity.kursplatform.org.application.OrganizationResultSerializer;
import org.mepcity.kursplatform.org.domain.Organization;
import org.mepcity.kursplatform.org.infrastructure.OrganizationResultSerializationException;

/** Jackson adapter keeps replay payload escaping and Unicode handling identical to HTTP JSON. */
public final class JacksonOrganizationResultSerializer implements OrganizationResultSerializer {
    private final ObjectMapper objectMapper;
    public JacksonOrganizationResultSerializer(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    @Override public String serialize(Organization organization) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", organization.id());
        result.put("name", organization.name());
        result.put("shortName", organization.shortName());
        result.put("defaultTimezone", organization.defaultTimezone());
        result.put("status", organization.status().name());
        result.put("createdAt", organization.createdAt().toString());
        result.put("updatedAt", organization.updatedAt().toString());
        result.put("rowVersion", organization.rowVersion());
        try { return objectMapper.writeValueAsString(result); }
        catch (JsonProcessingException exception) { throw new OrganizationResultSerializationException(exception); }
    }
}
