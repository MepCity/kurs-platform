package org.mepcity.kursplatform.org.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mepcity.kursplatform.org.application.BrandSettings;
import org.mepcity.kursplatform.org.application.OrganizationBrandAuthentication;
import org.mepcity.kursplatform.org.application.OrganizationBrandService;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** File-free brand endpoints. Logo storage/transfer is deliberately not part of ORG-005. */
@RestController
@RequestMapping(value = "/api/v1/organizations/{organizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
public final class OrganizationBrandController {
    private final OrganizationBrandService service;
    private final OrganizationBrandAuthentication authentication;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public OrganizationBrandController(OrganizationBrandService service,
                                       OrganizationBrandAuthentication authentication,
                                       Clock clock, ObjectMapper objectMapper) {
        this.service = service;
        this.authentication = authentication;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/brand")
    public ResponseEntity<BrandSettings.Brand> brand(@PathVariable UUID organizationId,
                                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        Actor actor = actor(authorization, organizationId);
        BrandSettings.Brand response = service.brand(actor.id(), organizationId, actor.platform(), requestId());
        return withEtag(response, response.rowVersion());
    }

    @PatchMapping(value = "/brand", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BrandSettings.Brand> updateBrand(
            @PathVariable UUID organizationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "If-Match-Row-Version", required = false) String rowVersion,
            @RequestBody(required = false) String rawBody) {
        Actor actor = actor(authorization, organizationId);
        BrandPatch body = parse(rawBody, BrandPatch.class);
        BrandSettings.Brand response = service.updateBrand(command(actor, organizationId, rowVersion,
                idempotencyKey, "ORG_UPDATE_BRAND", canonicalBrand(body)), body.primaryColor(), body.secondaryColor());
        return withEtag(response, response.rowVersion());
    }

    @GetMapping("/brand-colors")
    public ResponseEntity<BrandSettings.Palette> palette(@PathVariable UUID organizationId,
                                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        Actor actor = actor(authorization, organizationId);
        BrandSettings.Palette response = service.palette(actor.id(), organizationId, actor.platform(), requestId());
        return withEtag(response, response.rowVersion());
    }

    @PutMapping(value = "/brand-colors", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BrandSettings.Palette> updatePalette(
            @PathVariable UUID organizationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "If-Match-Row-Version", required = false) String rowVersion,
            @RequestBody(required = false) String rawBody) {
        Actor actor = actor(authorization, organizationId);
        PalettePut body = parse(rawBody, PalettePut.class);
        BrandSettings.Palette response = service.updatePalette(command(actor, organizationId, rowVersion,
                idempotencyKey, "ORG_UPDATE_BRAND_COLORS", canonicalColors(body.items())), colors(body.items()));
        return withEtag(response, response.rowVersion());
    }

    @GetMapping("/modules")
    public ResponseEntity<BrandSettings.Modules> modules(@PathVariable UUID organizationId,
                                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        Actor actor = actor(authorization, organizationId);
        BrandSettings.Modules response = service.modules(actor.id(), organizationId, actor.platform(), requestId());
        return withEtag(response, response.rowVersion());
    }

    @PatchMapping(value = "/modules", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BrandSettings.Modules> updateModules(
            @PathVariable UUID organizationId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "If-Match-Row-Version", required = false) String rowVersion,
            @RequestBody(required = false) String rawBody) {
        Actor actor = actor(authorization, organizationId);
        ModulesPatch body = parse(rawBody, ModulesPatch.class);
        BrandSettings.Modules response = service.updateModules(command(actor, organizationId, rowVersion,
                idempotencyKey, "ORG_UPDATE_MODULES", canonicalModules(body.items())), modules(body.items()));
        return withEtag(response, response.rowVersion());
    }

    private OrganizationBrandService.Command command(Actor actor, UUID organizationId, String rowVersion,
                                                     String key, String operation, String canonicalBody) {
        if (key == null || !key.matches("[\\x21-\\x7E]{1,128}")) throw new InvalidRequestException();
        int version;
        try {
            version = Integer.parseInt(rowVersion);
        } catch (RuntimeException exception) {
            throw new InvalidRequestException();
        }
        if (version <= 0) throw new InvalidRequestException();
        String method = operation.equals("ORG_UPDATE_BRAND_COLORS") ? "PUT" : "PATCH";
        String endpoint = switch (operation) {
            case "ORG_UPDATE_BRAND" -> "/brand";
            case "ORG_UPDATE_BRAND_COLORS" -> "/brand-colors";
            default -> "/modules";
        };
        String path = "/api/v1/organizations/" + organizationId + endpoint;
        String fingerprint = sha(method + "\n" + path + "\n" + operation + "\n" + organizationId
                + "\n" + sha(canonicalBody) + "\n" + version);
        return new OrganizationBrandService.Command(actor.id(), organizationId, actor.platform(), version, key,
                operation, fingerprint, requestId(), UUID.randomUUID().toString(), clock.instant().plus(Duration.ofMinutes(2)),
                clock.instant().plus(Duration.ofDays(30)));
    }

    private Actor actor(String authorization, UUID target) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= 7 || authorization.substring(7).isBlank()) {
            throw new UnauthenticatedException();
        }
        OrganizationBrandAuthentication.Actor resolved = authentication.authenticate(authorization.substring(7), target);
        return new Actor(resolved.userId(), resolved.platformAdministrator());
    }

    private static String canonicalBrand(BrandPatch body) {
        if (body == null) throw new ValidationException("body.REQUIRED");
        return "primaryColor=" + body.primaryColor() + ";secondaryColor=" + body.secondaryColor();
    }

    private static String canonicalColors(List<ColorRequest> items) {
        if (items == null) throw new ValidationException("items.REQUIRED");
        items.forEach(OrganizationBrandController::color);
        return items.stream().map(OrganizationBrandController::color).sorted(Comparator.comparingInt(BrandSettings.Color::sortOrder)
                        .thenComparing(BrandSettings.Color::colorHex))
                .map(item -> item.colorHex() + ":" + item.sortOrder()).collect(Collectors.joining(","));
    }

    private static String canonicalModules(List<ModulePatchRequest> items) {
        if (items == null) throw new ValidationException("items.REQUIRED");
        for (ModulePatchRequest item : items) {
            if (item == null) throw new ValidationException("items.REQUIRED");
            if (item.moduleCode() == null) throw new ValidationException("moduleCode.REQUIRED");
            if (item.isEnabled() == null && item.sortOrder() == null) throw new ValidationException("items.REQUIRED");
        }
        return items.stream().sorted(Comparator.comparing(ModulePatchRequest::moduleCode,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(item -> item == null ? "null" : item.moduleCode() + ":" + item.isEnabled() + ":" + item.sortOrder())
                .collect(Collectors.joining(","));
    }

    private static String sha(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Fingerprint oluşturulamadı", exception);
        }
    }

    private <T> T parse(String rawBody, Class<T> type) {
        if (rawBody == null || rawBody.isBlank()) throw new ValidationException("body.REQUIRED");
        try {
            T parsed = objectMapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(rawBody);
            if (parsed == null) throw new ValidationException("body.REQUIRED");
            return parsed;
        } catch (com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException exception) {
            throw new ValidationException("body.UNKNOWN");
        } catch (JsonProcessingException exception) {
            throw new InvalidRequestException();
        }
    }

    private static List<BrandSettings.Color> colors(List<ColorRequest> items) {
        if (items == null) throw new ValidationException("items.REQUIRED");
        return items.stream().map(OrganizationBrandController::color).toList();
    }

    private static BrandSettings.Color color(ColorRequest item) {
        if (item == null) throw new ValidationException("items.REQUIRED");
        if (item.colorHex() == null) throw new ValidationException("colorHex.REQUIRED");
        if (item.sortOrder() == null) throw new ValidationException("sortOrder.REQUIRED");
        return new BrandSettings.Color(item.colorHex(), item.sortOrder());
    }

    private static List<BrandSettings.ModulePatch> modules(List<ModulePatchRequest> items) {
        if (items == null) throw new ValidationException("items.REQUIRED");
        return items.stream().map(item -> {
            if (item == null) throw new ValidationException("items.REQUIRED");
            if (item.moduleCode() == null) throw new ValidationException("moduleCode.REQUIRED");
            if (item.isEnabled() == null && item.sortOrder() == null) throw new ValidationException("items.REQUIRED");
            return new BrandSettings.ModulePatch(item.moduleCode(), item.isEnabled(), item.sortOrder());
        }).toList();
    }

    private static String requestId() {
        String requestId = MDC.get("requestId");
        return requestId == null ? UUID.randomUUID().toString() : requestId;
    }
    private static <T> ResponseEntity<T> withEtag(T body, int rowVersion) { return ResponseEntity.ok().eTag("\"" + rowVersion + "\"").body(body); }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record BrandPatch(String primaryColor, String secondaryColor) { }
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PalettePut(List<ColorRequest> items) { }
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ModulesPatch(List<ModulePatchRequest> items) { }
    public record ColorRequest(String colorHex, Integer sortOrder) { }
    public record ModulePatchRequest(String moduleCode, Boolean isEnabled, Integer sortOrder) { }
    private record Actor(UUID id, boolean platform) { }
}
