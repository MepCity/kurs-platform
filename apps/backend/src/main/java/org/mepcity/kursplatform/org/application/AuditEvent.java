package org.mepcity.kursplatform.org.application;

import java.util.UUID;

/**
 * Typed, validated audit event produced by the ORG lifecycle transaction.
 *
 * <p>This record is <strong>closed</strong>: there is no public canonical constructor, no
 * Map/JSON-based creation path, and no reflection-free way to bypass the {@link Factory}. Each
 * action type is built through a {@code Factory} method that constructs dedicated typed value
 * objects ({@link StatusPayload}, {@link SettingPayload}, {@link AccessMetadata}, ...) and validates
 * every field against the V2 {@code audit_action_catalog} payload schema: allowed fields,
 * {@code requiredNull} invariants, closed {@code status}/{@code operationCode}/{@code outcome}/
 * {@code reasonCode} enums, rowVersion/count ranges. Unknown payload fields, unknown metadata keys
 * and invalid values are rejected at construction time, before any database write is attempted.
 *
 * <p>The record only exposes typed accessors; serialization is performed by the infrastructure
 * adapter using the typed value objects directly (no {@code toString()} of arbitrary objects).
 */
public final class AuditEvent {
    private final UUID id;
    private final UUID organizationId;
    private final UUID actorUserId;
    private final String requestId;
    private final String actionType;
    private final short payloadSchemaVersion;
    private final EventScope eventScope;
    private final String targetEntityType;
    private final EventKind eventKind;
    private final UUID targetEntityId;
    private final AuditPayload oldValue;
    private final AuditPayload newValue;
    private final AuditMetadata eventMetadata;
    private final String reasonCode;

    public enum EventScope { ORGANIZATION }
    public enum EventKind { DATA_MUTATION, ACCESS }

    /** Closed status set for {@code ORG_CREATED}/{@code ORG_STATUS_CHANGED} payloads. */
    public enum OrgStatus { ACTIVE, SUSPENDED, ARCHIVED }

    /**
     * Closed operation-code set accepted by the ORG factory methods: ORG-001's 5 lifecycle codes
     * plus ORG-002's 9 brand/palette/module/logo codes (the same canonical allow-list enforced by
     * the {@code platform_administrators} ORGANIZATION-scope RLS policy in {@code V3__...}).
     */
    public enum OperationCode {
        ORG_CREATE, ORG_LIST, ORG_DETAIL, ORG_UPDATE_IDENTITY, ORG_SUSPEND, ORG_ACTIVATE, ORG_ARCHIVE,
        ORG_VIEW_BRAND, ORG_UPDATE_BRAND, ORG_VIEW_BRAND_COLORS, ORG_UPDATE_BRAND_COLORS,
        ORG_VIEW_MODULES, ORG_UPDATE_MODULES, ORG_UPLOAD_LOGO, ORG_REMOVE_LOGO, ORG_VIEW_LOGO;

        public static OperationCode parse(String value) {
            try {
                return OperationCode.valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid operation code: " + value, exception);
            }
        }
    }

    /** Closed outcome set for {@code PLATFORM_ADMIN_ORG_ACCESS} metadata. */
    public enum AccessOutcome { ALLOWED, FORBIDDEN }

    /**
     * Marker interface for typed audit payloads. Implementations are sealed to the four ORG payload
     * shapes so no uncontrolled Map can be substituted.
     */
    public sealed interface AuditPayload permits StatusPayload, SettingPayload, NullPayload {}

    /**
     * Marker interface for typed audit metadata. Implementations are sealed to the ORG metadata
     * shapes so no uncontrolled Map can be substituted.
     */
    public sealed interface AuditMetadata
            permits StatusChangedMetadata, SettingChangedMetadata, CreatedMetadata, AccessMetadata {}

    /** Null payload used by {@code ORG_CREATED} (oldValue) and {@code PLATFORM_ADMIN_ORG_ACCESS}. */
    public record NullPayload() implements AuditPayload {}

    /** Status + rowVersion payload used by {@code ORG_CREATED}/{@code ORG_STATUS_CHANGED}. */
    public record StatusPayload(OrgStatus status, int rowVersion) implements AuditPayload {
        public StatusPayload {
            java.util.Objects.requireNonNull(status, "status");
            if (rowVersion < 1) {
                throw new IllegalArgumentException("rowVersion must be >= 1");
            }
        }
    }

    /**
     * One row of the {@code organization_modules} full-snapshot array carried by {@code
     * enabledModules} (v1 and v2 alike): {@code {moduleCode, isEnabled, sortOrder}}, matching
     * {@code ORG_MARKA_AYARLARI_API_SOZLESMESI.md §2.8.2}/§6.4-6.5 field names 1:1.
     */
    public record ModuleState(String moduleCode, boolean isEnabled, int sortOrder) {
        public ModuleState {
            if (moduleCode == null || moduleCode.isBlank()) {
                throw new IllegalArgumentException("moduleCode must not be blank");
            }
            if (sortOrder < 0 || sortOrder > 999) {
                throw new IllegalArgumentException("sortOrder must be between 0 and 999");
            }
        }
    }

    /**
     * One row of the {@code organization_brand_colors} full-snapshot array carried by v2's {@code
     * brandColors}: {@code {colorHex, sortOrder}}, matching
     * {@code ORG_MARKA_AYARLARI_API_SOZLESMESI.md §2.8.2}/§6.3 field names 1:1.
     */
    public record BrandColor(String colorHex, int sortOrder) {
        private static final java.util.regex.Pattern HEX = java.util.regex.Pattern.compile("^#[0-9A-Fa-f]{6}$");

        public BrandColor {
            if (colorHex == null || !HEX.matcher(colorHex).matches()) {
                throw new IllegalArgumentException("colorHex must match #RRGGBB");
            }
            if (sortOrder < 0 || sortOrder > 999) {
                throw new IllegalArgumentException("sortOrder must be between 0 and 999");
            }
        }
    }

    /**
     * Identity/brand payload used by {@code ORG_SETTING_CHANGED} (v1 and v2). Only fields present
     * in {@code changedFields} are meaningful; an untouched field's {@code null}/empty value is
     * indistinguishable from an explicit clear unless the caller consults {@code changedFields} (the
     * serializer does exactly this: a changed field is always emitted, even as JSON {@code null} or
     * {@code []}, while an untouched field is omitted entirely). {@code secondaryColor}/{@code
     * brandColors} are v2-only (v1's catalog schema does not list them; {@link
     * Factory#orgSettingChanged} fail-closed rejects a {@link SettingChange} that touches either).
     * No arbitrary Map is accepted.
     */
    public record SettingPayload(
            String name,
            String shortName,
            String defaultTimezone,
            String primaryColor,
            String secondaryColor,
            UUID logoAssetId,
            java.util.List<ModuleState> enabledModules,
            java.util.List<BrandColor> brandColors,
            java.util.List<String> attendanceStatuses,
            java.util.Set<String> changedFields,
            int rowVersion) implements AuditPayload {
        public SettingPayload {
            if (rowVersion < 1) {
                throw new IllegalArgumentException("rowVersion must be >= 1");
            }
            enabledModules = enabledModules == null ? java.util.List.of() : java.util.List.copyOf(enabledModules);
            brandColors = brandColors == null ? java.util.List.of() : java.util.List.copyOf(brandColors);
            attendanceStatuses = attendanceStatuses == null ? java.util.List.of() : java.util.List.copyOf(attendanceStatuses);
            changedFields = changedFields == null ? java.util.Set.of() : java.util.Set.copyOf(changedFields);
        }
    }

    public record CreatedMetadata(OperationCode operationCode) implements AuditMetadata {
        public CreatedMetadata {
            java.util.Objects.requireNonNull(operationCode, "operationCode");
        }
    }

    public record StatusChangedMetadata(
            OperationCode operationCode,
            int revokedMembershipCount,
            int revokedFamilyCount,
            int revokedTokenCount) implements AuditMetadata {
        public StatusChangedMetadata {
            java.util.Objects.requireNonNull(operationCode, "operationCode");
            if (revokedMembershipCount < 0 || revokedFamilyCount < 0 || revokedTokenCount < 0) {
                throw new IllegalArgumentException("revoked counts must be >= 0");
            }
        }
    }

    public record SettingChangedMetadata(OperationCode operationCode) implements AuditMetadata {
        public SettingChangedMetadata {
            java.util.Objects.requireNonNull(operationCode, "operationCode");
        }
    }

    public record AccessMetadata(OperationCode operationCode, AccessOutcome outcome) implements AuditMetadata {
        public AccessMetadata {
            java.util.Objects.requireNonNull(operationCode, "operationCode");
            java.util.Objects.requireNonNull(outcome, "outcome");
        }
    }

    /**
     * Canonical constructor is private. Tests and callers must use {@link Factory}; there is no
     * public way to build an {@link AuditEvent} with arbitrary payloads or bypass validation.
     */
    private AuditEvent(
            UUID id, UUID organizationId, UUID actorUserId, String requestId, String actionType,
            short payloadSchemaVersion, EventScope eventScope, String targetEntityType, EventKind eventKind,
            UUID targetEntityId, AuditPayload oldValue, AuditPayload newValue, AuditMetadata eventMetadata,
            String reasonCode) {
        this.id = java.util.Objects.requireNonNull(id, "id");
        this.organizationId = java.util.Objects.requireNonNull(organizationId, "organizationId");
        this.actorUserId = java.util.Objects.requireNonNull(actorUserId, "actorUserId");
        this.requestId = requestId;
        this.actionType = java.util.Objects.requireNonNull(actionType, "actionType");
        this.payloadSchemaVersion = payloadSchemaVersion;
        this.eventScope = java.util.Objects.requireNonNull(eventScope, "eventScope");
        this.targetEntityType = java.util.Objects.requireNonNull(targetEntityType, "targetEntityType");
        this.eventKind = java.util.Objects.requireNonNull(eventKind, "eventKind");
        this.targetEntityId = java.util.Objects.requireNonNull(targetEntityId, "targetEntityId");
        this.oldValue = oldValue;
        this.newValue = java.util.Objects.requireNonNull(newValue, "newValue");
        this.eventMetadata = java.util.Objects.requireNonNull(eventMetadata, "eventMetadata");
        this.reasonCode = reasonCode;
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID actorUserId() { return actorUserId; }
    public String requestId() { return requestId; }
    public String actionType() { return actionType; }
    public short payloadSchemaVersion() { return payloadSchemaVersion; }
    public EventScope eventScope() { return eventScope; }
    public String targetEntityType() { return targetEntityType; }
    public EventKind eventKind() { return eventKind; }
    public UUID targetEntityId() { return targetEntityId; }
    public AuditPayload oldValue() { return oldValue; }
    public AuditPayload newValue() { return newValue; }
    public AuditMetadata eventMetadata() { return eventMetadata; }
    public String reasonCode() { return reasonCode; }

    /**
     * Builds {@link AuditEvent} instances for the four ORG action types. The factory is the only
     * public construction surface; it validates every field and uses the typed value objects
     * directly. Factory methods throw {@link IllegalArgumentException} for unknown fields, unknown
     * metadata keys, invalid enum values and out-of-range counts.
     */
    public static final class Factory {
        private static final java.util.Set<String> ACCESS_REASON_CODES = java.util.Set.of("FORBIDDEN");

        private final String requestId;

        public Factory(String requestId) {
            this.requestId = requestId;
        }

        public AuditEvent orgCreated(UUID auditLogId, UUID organizationId, UUID actorUserId, UUID targetEntityId,
                String operationCode, int rowVersion) {
            var code = OperationCode.parse(operationCode);
            if (code != OperationCode.ORG_CREATE) {
                throw new IllegalArgumentException("ORG_CREATED requires operation code ORG_CREATE");
            }
            var oldValue = new NullPayload();
            var newValue = new StatusPayload(OrgStatus.ACTIVE, rowVersion);
            var metadata = new CreatedMetadata(code);
            return new AuditEvent(auditLogId, organizationId, actorUserId, requestId, "ORG_CREATED", (short) 1,
                    EventScope.ORGANIZATION, "ORGANIZATION", EventKind.DATA_MUTATION, targetEntityId,
                    oldValue, newValue, metadata, null);
        }

        public AuditEvent orgStatusChanged(UUID auditLogId, UUID organizationId, UUID actorUserId, UUID targetEntityId,
                String operationCode, String oldStatus, String newStatus, int oldRowVersion, int newRowVersion,
                int revokedMembershipCount, int revokedFamilyCount, int revokedTokenCount) {
            var code = OperationCode.parse(operationCode);
            if (code != OperationCode.ORG_SUSPEND && code != OperationCode.ORG_ACTIVATE && code != OperationCode.ORG_ARCHIVE) {
                throw new IllegalArgumentException("ORG_STATUS_CHANGED requires ORG_SUSPEND/ORG_ACTIVATE/ORG_ARCHIVE");
            }
            var parsedOldStatus = parseStatus(oldStatus);
            var parsedNewStatus = parseStatus(newStatus);
            validateStatusTransition(code, parsedOldStatus, parsedNewStatus);
            var oldValue = new StatusPayload(parsedOldStatus, oldRowVersion);
            var newValue = new StatusPayload(parsedNewStatus, newRowVersion);
            var metadata = new StatusChangedMetadata(code, revokedMembershipCount, revokedFamilyCount, revokedTokenCount);
            return new AuditEvent(auditLogId, organizationId, actorUserId, requestId, "ORG_STATUS_CHANGED", (short) 1,
                    EventScope.ORGANIZATION, "ORGANIZATION", EventKind.DATA_MUTATION, targetEntityId,
                    oldValue, newValue, metadata, null);
        }

        /**
         * Builds a {@code payload_schema_version=1} {@code ORG_SETTING_CHANGED} event (ORG-001
         * identity updates) from typed changed-field builders. Each builder accepts only the closed
         * v1 catalog field set; no {@code Map<String,Object>} is accepted. Fail-closed rejects a
         * {@link SettingChange} touching {@code secondaryColor}/{@code brandColors} -- those are
         * v2-only fields the v1 catalog row does not allow; use {@link #orgSettingChangedV2} instead.
         */
        public AuditEvent orgSettingChanged(UUID auditLogId, UUID organizationId, UUID actorUserId, UUID targetEntityId,
                String operationCode, SettingChange change, int newRowVersion) {
            var code = OperationCode.parse(operationCode);
            if (code != OperationCode.ORG_UPDATE_IDENTITY) {
                throw new IllegalArgumentException("ORG_SETTING_CHANGED requires operation code ORG_UPDATE_IDENTITY");
            }
            if (change == null) {
                throw new IllegalArgumentException("SettingChange must not be null");
            }
            if (!change.hasChange()) {
                throw new IllegalArgumentException("SettingChange must have at least one changed field");
            }
            if (change.touchesV2OnlyFields()) {
                throw new IllegalArgumentException(
                        "v1 ORG_SETTING_CHANGED does not accept secondaryColor/brandColors; use orgSettingChangedV2");
            }
            var oldValue = change.toOldPayload(newRowVersion - 1);
            var newValue = change.toNewPayload(newRowVersion);
            var metadata = new SettingChangedMetadata(code);
            return new AuditEvent(auditLogId, organizationId, actorUserId, requestId, "ORG_SETTING_CHANGED", (short) 1,
                    EventScope.ORGANIZATION, "ORGANIZATION", EventKind.DATA_MUTATION, targetEntityId,
                    oldValue, newValue, metadata, null);
        }

        /** Closed operation-code -> allowed-changed-field-set matrix for {@link #orgSettingChangedV2}. */
        private static final java.util.Map<OperationCode, java.util.Set<String>> V2_OPERATION_FIELD_MATRIX = java.util.Map.of(
                OperationCode.ORG_UPDATE_BRAND, java.util.Set.of("primaryColor", "secondaryColor"),
                OperationCode.ORG_UPDATE_BRAND_COLORS, java.util.Set.of("brandColors"),
                OperationCode.ORG_UPDATE_MODULES, java.util.Set.of("enabledModules"),
                OperationCode.ORG_UPLOAD_LOGO, java.util.Set.of("logoAssetId"),
                OperationCode.ORG_REMOVE_LOGO, java.util.Set.of("logoAssetId"));

        /**
         * Builds a {@code payload_schema_version=2} {@code ORG_SETTING_CHANGED} event (ORG-002/
         * ORG-005 brand/palette/module/logo updates): allows {@code secondaryColor}/{@code
         * brandColors} in addition to every v1 field. {@code operationCode} must be one of the five
         * ORG-002 write codes -- {@code ORG_UPDATE_BRAND}, {@code ORG_UPDATE_BRAND_COLORS}, {@code
         * ORG_UPDATE_MODULES}, {@code ORG_UPLOAD_LOGO}, {@code ORG_REMOVE_LOGO} -- matching the
         * {@code audit_logs_insert_org_setting_changed} RLS policy's allow-list. Each operation code
         * may only touch its own closed field subset ({@link #V2_OPERATION_FIELD_MATRIX}): {@code
         * ORG_UPDATE_BRAND} -> {@code primaryColor}/{@code secondaryColor} only, {@code
         * ORG_UPDATE_BRAND_COLORS} -> {@code brandColors} only, {@code ORG_UPDATE_MODULES} -> {@code
         * enabledModules} only, {@code ORG_UPLOAD_LOGO}/{@code ORG_REMOVE_LOGO} -> {@code
         * logoAssetId} only. {@code ORG_VIEW_LOGO} (read-only) is never accepted here -- it cannot
         * produce {@code ORG_SETTING_CHANGED} at all. A mixed/foreign field for the given operation
         * code is rejected fail-fast, never silently narrowed or ignored.
         */
        public AuditEvent orgSettingChangedV2(UUID auditLogId, UUID organizationId, UUID actorUserId, UUID targetEntityId,
                String operationCode, SettingChange change, int newRowVersion) {
            var code = OperationCode.parse(operationCode);
            var allowedFields = V2_OPERATION_FIELD_MATRIX.get(code);
            if (allowedFields == null) {
                throw new IllegalArgumentException(
                        "ORG_SETTING_CHANGED v2 requires ORG_UPDATE_BRAND/ORG_UPDATE_BRAND_COLORS/"
                                + "ORG_UPDATE_MODULES/ORG_UPLOAD_LOGO/ORG_REMOVE_LOGO");
            }
            if (change == null) {
                throw new IllegalArgumentException("SettingChange must not be null");
            }
            if (!change.hasChange()) {
                throw new IllegalArgumentException("SettingChange must have at least one changed field");
            }
            if (!allowedFields.containsAll(change.changedFields())) {
                throw new IllegalArgumentException(
                        "SettingChange touches fields not allowed for " + code + ": " + change.changedFields()
                                + " (allowed: " + allowedFields + ")");
            }
            var oldValue = change.toOldPayload(newRowVersion - 1);
            var newValue = change.toNewPayload(newRowVersion);
            var metadata = new SettingChangedMetadata(code);
            return new AuditEvent(auditLogId, organizationId, actorUserId, requestId, "ORG_SETTING_CHANGED", (short) 2,
                    EventScope.ORGANIZATION, "ORGANIZATION", EventKind.DATA_MUTATION, targetEntityId,
                    oldValue, newValue, metadata, null);
        }

        public AuditEvent platformAdminOrgAccess(UUID auditLogId, UUID organizationId, UUID actorUserId,
                UUID targetEntityId, String operationCode, String outcome, String reasonCode) {
            var code = OperationCode.parse(operationCode);
            var outcomeEnum = parseOutcome(outcome);
            if (reasonCode != null && !ACCESS_REASON_CODES.contains(reasonCode)) {
                throw new IllegalArgumentException("Invalid reason code: " + reasonCode);
            }
            if (outcomeEnum == AccessOutcome.ALLOWED && reasonCode != null) {
                throw new IllegalArgumentException("ALLOWED outcome must not carry a reason code");
            }
            if (outcomeEnum == AccessOutcome.FORBIDDEN && !"FORBIDDEN".equals(reasonCode)) {
                throw new IllegalArgumentException("FORBIDDEN outcome requires reason code FORBIDDEN");
            }
            var empty = new NullPayload();
            var metadata = new AccessMetadata(code, outcomeEnum);
            return new AuditEvent(auditLogId, organizationId, actorUserId, requestId, "PLATFORM_ADMIN_ORG_ACCESS",
                    (short) 1, EventScope.ORGANIZATION, "ORGANIZATION", EventKind.ACCESS, targetEntityId,
                    empty, empty, metadata, reasonCode);
        }

        private static void validateStatusTransition(OperationCode code, OrgStatus oldStatus, OrgStatus newStatus) {
            boolean valid = switch (code) {
                case ORG_SUSPEND -> oldStatus == OrgStatus.ACTIVE && newStatus == OrgStatus.SUSPENDED;
                case ORG_ACTIVATE -> oldStatus == OrgStatus.SUSPENDED && newStatus == OrgStatus.ACTIVE;
                case ORG_ARCHIVE -> (oldStatus == OrgStatus.ACTIVE || oldStatus == OrgStatus.SUSPENDED)
                        && newStatus == OrgStatus.ARCHIVED;
                default -> false;
            };
            if (!valid) {
                throw new IllegalArgumentException(
                        "Invalid status transition " + oldStatus + " -> " + newStatus + " for " + code);
            }
        }

        private static OrgStatus parseStatus(String value) {
            try {
                return OrgStatus.valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid org status: " + value, exception);
            }
        }

        private static AccessOutcome parseOutcome(String value) {
            try {
                return AccessOutcome.valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid access outcome: " + value, exception);
            }
        }
    }

    /**
     * Closed builder for {@code ORG_SETTING_CHANGED} changed fields. Only the catalog-allowed fields
     * are settable; at least one field must change. The builder is the only way to construct a
     * {@link SettingPayload} for an {@code ORG_SETTING_CHANGED} event.
     */
    public static final class SettingChange {
        private static final java.util.Set<String> V2_ONLY_FIELDS = java.util.Set.of("secondaryColor", "brandColors");

        /** The exact, closed set of manageable module codes (`AGENT_GOREV_PLANI.md` §5 / `ORG_MARKA_AYARLARI_API_SOZLESMESI.md` §6.5). */
        private static final java.util.Set<String> MANAGEABLE_MODULE_CODES =
                java.util.Set.of("ATT", "CONTENT", "PROGRAM", "PROGRESS", "EXPORT", "AUDIT");
        private static final int MAX_BRAND_COLORS = 20;

        private String oldName;
        private String newName;
        private String oldShortName;
        private String newShortName;
        private String oldDefaultTimezone;
        private String newDefaultTimezone;
        private String oldPrimaryColor;
        private String newPrimaryColor;
        private String oldSecondaryColor;
        private String newSecondaryColor;
        private UUID oldLogoAssetId;
        private UUID newLogoAssetId;
        private java.util.List<ModuleState> oldEnabledModules;
        private java.util.List<ModuleState> newEnabledModules;
        private java.util.List<BrandColor> oldBrandColors;
        private java.util.List<BrandColor> newBrandColors;
        private java.util.List<String> oldAttendanceStatuses;
        private java.util.List<String> newAttendanceStatuses;
        private final java.util.Set<String> changedFields = new java.util.LinkedHashSet<>();

        public SettingChange name(String oldName, String newName) {
            requireChanged(oldName, newName, "name");
            this.oldName = oldName;
            this.newName = newName;
            return this;
        }

        public SettingChange shortName(String oldShortName, String newShortName) {
            requireChanged(oldShortName, newShortName, "shortName");
            this.oldShortName = oldShortName;
            this.newShortName = newShortName;
            return this;
        }

        public SettingChange defaultTimezone(String oldTz, String newTz) {
            requireChanged(oldTz, newTz, "defaultTimezone");
            this.oldDefaultTimezone = oldTz;
            this.newDefaultTimezone = newTz;
            return this;
        }

        public SettingChange primaryColor(String oldColor, String newColor) {
            requireChanged(oldColor, newColor, "primaryColor");
            this.oldPrimaryColor = oldColor;
            this.newPrimaryColor = newColor;
            return this;
        }

        public SettingChange logoAssetId(UUID oldId, UUID newId) {
            requireChanged(oldId, newId, "logoAssetId");
            this.oldLogoAssetId = oldId;
            this.newLogoAssetId = newId;
            return this;
        }

        /** v2-only. Rejected by {@link Factory#orgSettingChanged} (v1); see {@link #touchesV2OnlyFields}. */
        public SettingChange secondaryColor(String oldColor, String newColor) {
            requireChanged(oldColor, newColor, "secondaryColor");
            this.oldSecondaryColor = oldColor;
            this.newSecondaryColor = newColor;
            return this;
        }

        /**
         * Full {@code organization_modules} snapshot (v1 and v2 alike): both lists must contain
         * every one of the six fixed module codes exactly once (enabled and disabled), {@code
         * sortOrder} in {@code [0, 999]}. The caller's ordering is never trusted: each list is
         * validated and canonicalized (sorted by {@code sortOrder} ascending, ties broken by
         * {@code moduleCode} alphabetically) before being compared or stored, so a
         * differently-ordered-but-equivalent snapshot is never mistaken for a real change and the
         * persisted JSON is always canonical regardless of caller order.
         */
        public SettingChange enabledModules(java.util.List<ModuleState> oldModules, java.util.List<ModuleState> newModules) {
            var canonicalOld = canonicalModuleSnapshot(oldModules);
            var canonicalNew = canonicalModuleSnapshot(newModules);
            requireChanged(canonicalOld, canonicalNew, "enabledModules");
            this.oldEnabledModules = canonicalOld;
            this.newEnabledModules = canonicalNew;
            return this;
        }

        /**
         * v2-only. Full {@code organization_brand_colors} snapshot: at most 20 unique {@code
         * colorHex} entries, {@code sortOrder} in {@code [0, 999]}. Rejected by v1; see
         * {@link #touchesV2OnlyFields}. The caller's ordering is never trusted: canonicalized
         * (sorted by {@code sortOrder} ascending, ties broken by {@code colorHex} alphabetically)
         * before being compared or stored.
         */
        public SettingChange brandColors(java.util.List<BrandColor> oldColors, java.util.List<BrandColor> newColors) {
            var canonicalOld = canonicalBrandColors(oldColors);
            var canonicalNew = canonicalBrandColors(newColors);
            requireChanged(canonicalOld, canonicalNew, "brandColors");
            this.oldBrandColors = canonicalOld;
            this.newBrandColors = canonicalNew;
            return this;
        }

        /**
         * Validates and canonicalizes a module snapshot: exactly the six {@link
         * #MANAGEABLE_MODULE_CODES}, no duplicates/unknowns/omissions, {@code sortOrder} already
         * range-checked by {@link ModuleState}'s own constructor. Fail-fast on any violation.
         */
        private static java.util.List<ModuleState> canonicalModuleSnapshot(java.util.List<ModuleState> modules) {
            if (modules == null) {
                throw new IllegalArgumentException("enabledModules snapshot must not be null");
            }
            var seen = new java.util.HashSet<String>();
            for (var module : modules) {
                if (!MANAGEABLE_MODULE_CODES.contains(module.moduleCode())) {
                    throw new IllegalArgumentException("Unknown module code: " + module.moduleCode());
                }
                if (!seen.add(module.moduleCode())) {
                    throw new IllegalArgumentException("Duplicate module code: " + module.moduleCode());
                }
            }
            if (!seen.equals(MANAGEABLE_MODULE_CODES)) {
                throw new IllegalArgumentException(
                        "enabledModules snapshot must contain exactly " + MANAGEABLE_MODULE_CODES + ", got " + seen);
            }
            var canonical = new java.util.ArrayList<>(modules);
            canonical.sort(java.util.Comparator.comparingInt(ModuleState::sortOrder).thenComparing(ModuleState::moduleCode));
            return java.util.List.copyOf(canonical);
        }

        /**
         * Validates and canonicalizes a brand-color palette: at most {@link #MAX_BRAND_COLORS}
         * entries, no duplicate {@code colorHex}, {@code sortOrder} already range-checked by
         * {@link BrandColor}'s own constructor. Fail-fast on any violation.
         */
        private static java.util.List<BrandColor> canonicalBrandColors(java.util.List<BrandColor> colors) {
            if (colors == null) {
                throw new IllegalArgumentException("brandColors must not be null");
            }
            if (colors.size() > MAX_BRAND_COLORS) {
                throw new IllegalArgumentException("brandColors must contain at most " + MAX_BRAND_COLORS + " colors");
            }
            var seen = new java.util.HashSet<String>();
            for (var color : colors) {
                if (!seen.add(color.colorHex())) {
                    throw new IllegalArgumentException("Duplicate colorHex: " + color.colorHex());
                }
            }
            var canonical = new java.util.ArrayList<>(colors);
            canonical.sort(java.util.Comparator.comparingInt(BrandColor::sortOrder).thenComparing(BrandColor::colorHex));
            return java.util.List.copyOf(canonical);
        }

        public SettingChange attendanceStatuses(java.util.List<String> oldStatuses, java.util.List<String> newStatuses) {
            requireChanged(oldStatuses, newStatuses, "attendanceStatuses");
            this.oldAttendanceStatuses = oldStatuses;
            this.newAttendanceStatuses = newStatuses;
            return this;
        }

        /** {@code true} once at least one field setter has recorded an actual change. */
        boolean hasChange() {
            return !changedFields.isEmpty();
        }

        /** {@code true} if {@code secondaryColor} and/or {@code brandColors} was touched (v2-only fields). */
        boolean touchesV2OnlyFields() {
            return !java.util.Collections.disjoint(changedFields, V2_ONLY_FIELDS);
        }

        /** Read-only view of the fields touched so far, for the Factory's operation-code matrix check. */
        java.util.Set<String> changedFields() {
            return java.util.Collections.unmodifiableSet(changedFields);
        }

        private void requireChanged(Object oldValue, Object newValue, String field) {
            if (java.util.Objects.equals(oldValue, newValue)) {
                throw new IllegalArgumentException("SettingChange field did not change: " + field);
            }
            changedFields.add(field);
        }

        SettingPayload toOldPayload(int rowVersion) {
            return new SettingPayload(oldName, oldShortName, oldDefaultTimezone, oldPrimaryColor, oldSecondaryColor,
                    oldLogoAssetId, oldEnabledModules, oldBrandColors, oldAttendanceStatuses, changedFields, rowVersion);
        }

        SettingPayload toNewPayload(int rowVersion) {
            return new SettingPayload(newName, newShortName, newDefaultTimezone, newPrimaryColor, newSecondaryColor,
                    newLogoAssetId, newEnabledModules, newBrandColors, newAttendanceStatuses, changedFields, rowVersion);
        }
    }
}
