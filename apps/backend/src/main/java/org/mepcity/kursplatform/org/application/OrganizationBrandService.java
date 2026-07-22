package org.mepcity.kursplatform.org.application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.mepcity.kursplatform.org.domain.Organization;
import org.mepcity.kursplatform.org.domain.OrganizationRepository;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Transactional implementation of the file-free ORG-005 brand, palette and module surface. */
public final class OrganizationBrandService {
    private static final String DEFAULT_PRIMARY = "#2E7D32";
    private static final String DEFAULT_SECONDARY = "#E65100";
    private final OrganizationRepository organizations;
    private final DataSource dataSource;
    private final TransactionTemplate transactions;
    private final AuditWriter auditWriter;
    private final IdempotencyRecorder idempotency;
    private final OrganizationBrandResultSerializer resultSerializer;
    private final OrganizationBrandRateLimiter rateLimiter;

    public OrganizationBrandService(OrganizationRepository organizations, DataSource dataSource,
            PlatformTransactionManager manager, AuditWriter auditWriter, IdempotencyRecorder idempotency,
            OrganizationBrandResultSerializer resultSerializer, OrganizationBrandRateLimiter rateLimiter) {
        this.organizations = organizations; this.dataSource = dataSource; this.transactions = new TransactionTemplate(manager);
        this.auditWriter = auditWriter; this.idempotency = idempotency; this.resultSerializer = resultSerializer;
        this.rateLimiter = rateLimiter;
    }

    public BrandSettings.Brand brand(UUID actor, UUID organizationId, boolean platformAdmin, String requestId) {
        return transactions.execute(s -> { context(actor, organizationId, "ORG_VIEW_BRAND", platformAdmin); rateLimiter.check(actor, organizationId, "ORG_VIEW_BRAND"); Organization o = visible(organizationId);
            if (platformAdmin) accessAudit(actor, organizationId, "ORG_VIEW_BRAND", requestId);
            return new BrandSettings.Brand(orDefault(o.primaryColor(), DEFAULT_PRIMARY), orDefault(o.secondaryColor(), DEFAULT_SECONDARY), o.rowVersion(), null); });
    }

    public BrandSettings.Brand updateBrand(Command command, String primary, String secondary) {
        if (primary == null && secondary == null) throw new IllegalArgumentException("body.REQUIRED");
        return transactions.execute(s -> {
            context(command.actor(), command.organizationId(), "ORG_UPDATE_BRAND", command.platformAdmin());
            Claim claim = claim(command); if (claim.replay()) return resultSerializer.deserializeBrand(claim.replayResult().resultPayload());
            rateLimiter.check(command.actor(), command.organizationId(), command.operation());
            Organization old = lock(command.organizationId()); verifyVersion(old, command.rowVersion());
            // Missing fields are deliberately not materialized to theme defaults: primary and secondary
            // remain independently editable, while reads render the documented defaults.
            String nextPrimary = primary == null ? old.primaryColor() : BrandColorValidator.validateBrandColor("primaryColor", primary);
            String nextSecondary = secondary == null ? old.secondaryColor() : BrandColorValidator.validateBrandColor("secondaryColor", secondary);
            if (java.util.Objects.equals(nextPrimary, old.primaryColor()) && java.util.Objects.equals(nextSecondary, old.secondaryColor())) {
                if (command.platformAdmin()) accessAudit(command.actor(), old.id(), "ORG_UPDATE_BRAND", command.requestId());
                BrandSettings.Brand response = new BrandSettings.Brand(orDefault(nextPrimary, DEFAULT_PRIMARY), orDefault(nextSecondary, DEFAULT_SECONDARY), old.rowVersion(), null);
                complete(claim, old.id(), command, resultSerializer.serialize(response));
                return response;
            }
            Organization changed = organizations.updateBrand(old.id(), nextPrimary, nextSecondary, old.rowVersion(), command.actor()).orElseThrow(OrganizationConflictException::new);
            AuditEvent.SettingChange change = new AuditEvent.SettingChange();
            if (!java.util.Objects.equals(nextPrimary, old.primaryColor())) change.primaryColor(old.primaryColor(), nextPrimary);
            if (!java.util.Objects.equals(nextSecondary, old.secondaryColor())) change.secondaryColor(old.secondaryColor(), nextSecondary);
            auditWriter.write(new AuditEvent.Factory(command.requestId()).orgSettingChangedV2(UUID.randomUUID(), changed.id(), command.actor(), changed.id(), "ORG_UPDATE_BRAND", change, changed.rowVersion()));
            if (command.platformAdmin()) accessAudit(command.actor(), changed.id(), "ORG_UPDATE_BRAND", command.requestId());
            BrandSettings.Brand response = new BrandSettings.Brand(orDefault(nextPrimary, DEFAULT_PRIMARY), orDefault(nextSecondary, DEFAULT_SECONDARY), changed.rowVersion(), null);
            complete(claim, changed.id(), command, resultSerializer.serialize(response));
            return response;
        });
    }

    public BrandSettings.Palette palette(UUID actor, UUID organizationId, boolean platformAdmin, String requestId) {
        return transactions.execute(s -> { context(actor, organizationId, "ORG_VIEW_BRAND_COLORS", platformAdmin); rateLimiter.check(actor, organizationId, "ORG_VIEW_BRAND_COLORS"); Organization o=visible(organizationId);
            if (platformAdmin) accessAudit(actor, organizationId, "ORG_VIEW_BRAND_COLORS", requestId);
            return new BrandSettings.Palette(o.rowVersion(), readPalette()); });
    }

    public BrandSettings.Palette updatePalette(Command command, List<BrandSettings.Color> items) {
        List<BrandSettings.Color> normalized = normalizeColors(items);
        return transactions.execute(s -> {
            context(command.actor(), command.organizationId(), "ORG_UPDATE_BRAND_COLORS", command.platformAdmin());
            Claim claim=claim(command); if(claim.replay()) return resultSerializer.deserializePalette(claim.replayResult().resultPayload());
            rateLimiter.check(command.actor(), command.organizationId(), command.operation());
            Organization old=lock(command.organizationId()); verifyVersion(old,command.rowVersion()); List<BrandSettings.Color> before=readPalette();
            if (before.equals(normalized)) { if(command.platformAdmin()) accessAudit(command.actor(),old.id(),"ORG_UPDATE_BRAND_COLORS",command.requestId()); BrandSettings.Palette response = new BrandSettings.Palette(old.rowVersion(), before); complete(claim, old.id(), command, resultSerializer.serialize(response)); return response; }
            Connection c=DataSourceUtils.getConnection(dataSource); try (PreparedStatement d=c.prepareStatement("DELETE FROM organization_brand_colors WHERE organization_id = ?")) { d.setObject(1,old.id()); d.executeUpdate(); }
            catch(SQLException e){throw new OrganizationPersistenceStateException("Renk paleti silinemedi",e);}
            try (PreparedStatement i=c.prepareStatement("INSERT INTO organization_brand_colors (organization_id,color_hex,sort_order) VALUES (?,?,?)")) { for(var color:normalized){i.setObject(1,old.id());i.setString(2,color.colorHex());i.setInt(3,color.sortOrder());i.addBatch();} i.executeBatch(); }
            catch(SQLException e){throw new OrganizationPersistenceStateException("Renk paleti yazılamadı",e);}
            Organization changed=organizations.updateBrand(old.id(),old.primaryColor(),old.secondaryColor(),old.rowVersion(),command.actor()).orElseThrow(OrganizationConflictException::new);
            AuditEvent.SettingChange change=new AuditEvent.SettingChange().brandColors(toAuditColors(before),toAuditColors(normalized));
            auditWriter.write(new AuditEvent.Factory(command.requestId()).orgSettingChangedV2(UUID.randomUUID(),changed.id(),command.actor(),changed.id(),"ORG_UPDATE_BRAND_COLORS",change,changed.rowVersion()));
            if(command.platformAdmin()) accessAudit(command.actor(),changed.id(),"ORG_UPDATE_BRAND_COLORS",command.requestId()); BrandSettings.Palette response = new BrandSettings.Palette(changed.rowVersion(),normalized); complete(claim,changed.id(),command,resultSerializer.serialize(response)); return response;
        });
    }

    public BrandSettings.Modules modules(UUID actor, UUID organizationId, boolean platformAdmin, String requestId) {
        return transactions.execute(s -> { context(actor,organizationId,"ORG_VIEW_MODULES",platformAdmin); rateLimiter.check(actor, organizationId, "ORG_VIEW_MODULES"); Organization o=visible(organizationId); if(platformAdmin) accessAudit(actor,organizationId,"ORG_VIEW_MODULES",requestId); return new BrandSettings.Modules(o.rowVersion(),readModules()); });
    }

    public BrandSettings.Modules updateModules(Command command, List<BrandSettings.ModulePatch> items) {
        return transactions.execute(s -> { context(command.actor(),command.organizationId(),"ORG_UPDATE_MODULES",command.platformAdmin()); Claim claim=claim(command); if(claim.replay()) return resultSerializer.deserializeModules(claim.replayResult().resultPayload()); rateLimiter.check(command.actor(), command.organizationId(), command.operation()); Organization old=lock(command.organizationId()); verifyVersion(old,command.rowVersion()); List<BrandSettings.Module> before=readModules(); List<BrandSettings.Module> normalized=mergeModules(before,items);
            if (before.equals(normalized)) { if(command.platformAdmin()) accessAudit(command.actor(),old.id(),"ORG_UPDATE_MODULES",command.requestId()); BrandSettings.Modules response = new BrandSettings.Modules(old.rowVersion(), before); complete(claim, old.id(), command, resultSerializer.serialize(response)); return response; }
            Connection c=DataSourceUtils.getConnection(dataSource); try(PreparedStatement u=c.prepareStatement("UPDATE organization_modules SET is_enabled=?, sort_order=?, updated_at=transaction_timestamp(), updated_by_user_id=? WHERE organization_id=? AND module_code=?")){ for(var m:normalized){u.setBoolean(1,m.isEnabled());u.setInt(2,m.sortOrder());u.setObject(3,command.actor());u.setObject(4,old.id());u.setString(5,m.moduleCode()); if(u.executeUpdate()!=1) throw new OrganizationConflictException();} }catch(SQLException e){throw new OrganizationPersistenceStateException("Modül ayarı yazılamadı",e);}
            Organization changed=organizations.updateBrand(old.id(),old.primaryColor(),old.secondaryColor(),old.rowVersion(),command.actor()).orElseThrow(OrganizationConflictException::new);
            AuditEvent.SettingChange change=new AuditEvent.SettingChange().enabledModules(toAuditModules(before),toAuditModules(normalized)); auditWriter.write(new AuditEvent.Factory(command.requestId()).orgSettingChangedV2(UUID.randomUUID(),changed.id(),command.actor(),changed.id(),"ORG_UPDATE_MODULES",change,changed.rowVersion())); if(command.platformAdmin()) accessAudit(command.actor(),changed.id(),"ORG_UPDATE_MODULES",command.requestId()); BrandSettings.Modules response = new BrandSettings.Modules(changed.rowVersion(),normalized); complete(claim,changed.id(),command,resultSerializer.serialize(response)); return response;
        });
    }

    private Organization visible(UUID id) { return organizations.findById(id).orElseThrow(OrganizationNotVisibleException::new); }
    private BrandSettings.Brand brandView() { Organization o = visible(currentOrganizationId()); return new BrandSettings.Brand(orDefault(o.primaryColor(), DEFAULT_PRIMARY), orDefault(o.secondaryColor(), DEFAULT_SECONDARY), o.rowVersion(), null); }
    private UUID currentOrganizationId() { try (PreparedStatement p = DataSourceUtils.getConnection(dataSource).prepareStatement("SELECT current_setting('app.organization_id', true)::uuid"); ResultSet r = p.executeQuery()) { r.next(); return r.getObject(1, UUID.class); } catch (SQLException e) { throw new OrganizationPersistenceStateException("Kurum bağlamı okunamadı", e); } }
    private Organization lock(UUID id) { return organizations.findByIdForUpdate(id).orElseThrow(OrganizationNotVisibleException::new); }
    private void verifyVersion(Organization organization, int expected) {
        if (organization.isArchived()) throw new OrganizationStateConflictException();
        if (organization.rowVersion() != expected) throw new OrganizationConflictException();
    }
    private Claim claim(Command c) {
        IdempotencyOutcome o = idempotency.resolveOrClaim("ORGANIZATION", c.organizationId(), c.actor(), c.key(), c.operation(), c.fingerprint(), c.leaseOwner(), c.leaseExpiry(), c.retentionExpiry());
        if (o instanceof IdempotencyOutcome.Clash) throw new IdempotencyKeyReusedException();
        if (o instanceof IdempotencyOutcome.Pending) throw new IdempotencyPendingException();
        if (o instanceof IdempotencyOutcome.Replay r) return new Claim(null, r.result(), true);
        return new Claim((IdempotencyOutcome.Claimed) o, null, false);
    }
    private void complete(Claim claim, UUID result, Command c, String payload) { idempotency.markCompleted(claim.claim().claimId(), claim.claim().leaseOwner(), claim.claim().leaseGeneration(), result, (short)200, payload, c.retentionExpiry()); }
    private void context(UUID actor, UUID org, String operation, boolean platform) {
        Connection c = DataSourceUtils.getConnection(dataSource);
        try (var st = c.createStatement()) {
            st.execute("SET LOCAL ROLE org_runtime");
            set(c, "app.iam_operation_scope", "ORGANIZATION");
            set(c, "app.iam_actor_user_id", actor.toString());
            set(c, "app.organization_id", org.toString());
            set(c, "app.iam_operation_code", operation);
            // The session claim is only a routing hint. The target transaction re-reads the
            // actor-only row before setting the support GUC, so a revoked admin cannot replay.
            if (platform) {
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT user_id FROM platform_administrators WHERE user_id = ? AND revoked_at IS NULL")) {
                    p.setObject(1, actor);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new ForbiddenException();
                    }
                }
                set(c, "app.iam_platform_admin_support_access", "true");
            } else {
                String permission = switch (operation) {
                    case "ORG_UPDATE_BRAND", "ORG_UPDATE_BRAND_COLORS" -> "BRAND_MANAGE";
                    case "ORG_UPDATE_MODULES" -> "MODULE_MANAGE";
                    default -> null;
                };
                try (PreparedStatement p = c.prepareStatement("SELECT org_actor_has_brand_access(?, ?, ?)")) {
                    p.setObject(1, org); p.setObject(2, actor); p.setString(3, permission);
                    try (ResultSet r = p.executeQuery()) {
                        r.next(); if (!r.getBoolean(1)) throw new ForbiddenException();
                    }
                }
            }
        } catch (SQLException e) {
            throw new OrganizationPersistenceStateException("ORG RLS bağlamı kurulamadı", e);
        }
    }
    private static void set(Connection c,String key,String value) throws SQLException { try(PreparedStatement p=c.prepareStatement("SELECT set_config(?, ?, true)")){p.setString(1,key);p.setString(2,value);p.execute();} }
    private void accessAudit(UUID actor, UUID org, String op, String request) { auditWriter.write(new AuditEvent.Factory(request).platformAdminOrgAccess(UUID.randomUUID(),org,actor,org,op,"ALLOWED",null)); }
    private List<BrandSettings.Color> readPalette(){ Connection c=DataSourceUtils.getConnection(dataSource); List<BrandSettings.Color> out=new ArrayList<>(); try(PreparedStatement p=c.prepareStatement("SELECT color_hex,sort_order FROM organization_brand_colors WHERE organization_id=current_setting('app.organization_id',true)::uuid ORDER BY sort_order,color_hex"); ResultSet r=p.executeQuery()){while(r.next())out.add(new BrandSettings.Color(r.getString(1),r.getInt(2)));return out;}catch(SQLException e){throw new OrganizationPersistenceStateException("Renk paleti okunamadı",e);} }
    private List<BrandSettings.Module> readModules(){ Connection c=DataSourceUtils.getConnection(dataSource); List<BrandSettings.Module> out=new ArrayList<>(); try(PreparedStatement p=c.prepareStatement("SELECT module_code,is_enabled,sort_order FROM organization_modules WHERE organization_id=current_setting('app.organization_id',true)::uuid ORDER BY sort_order,module_code"); ResultSet r=p.executeQuery()){while(r.next())out.add(new BrandSettings.Module(r.getString(1),r.getBoolean(2),r.getInt(3)));return out;}catch(SQLException e){throw new OrganizationPersistenceStateException("Modüller okunamadı",e);} }
    private static List<BrandSettings.Color> normalizeColors(List<BrandSettings.Color> input){if(input==null||input.size()>20)throw new IllegalArgumentException("items.INVALID"); List<BrandSettings.Color> out=new ArrayList<>(); HashSet<String> colors=new HashSet<>(); for(var i:input){if(i==null||i.sortOrder()<0||i.sortOrder()>999)throw new IllegalArgumentException("items.INVALID");String color=BrandColorValidator.validatePaletteColor(i.colorHex());if(!colors.add(color))throw new IllegalArgumentException("items.DUPLICATE");out.add(new BrandSettings.Color(color,i.sortOrder()));}out.sort(Comparator.comparingInt(BrandSettings.Color::sortOrder).thenComparing(BrandSettings.Color::colorHex));return out;}
    private static List<BrandSettings.Module> mergeModules(List<BrandSettings.Module> current,List<BrandSettings.ModulePatch> input){if(input==null||input.isEmpty()||input.size()>6)throw new IllegalArgumentException("items.INVALID"); var allowed=java.util.Set.of("ATT","PROGRAM","CONTENT","PROGRESS","EXPORT","AUDIT");var byCode=new java.util.HashMap<String,BrandSettings.Module>();for(var value:current)byCode.put(value.moduleCode(),value);HashSet<String> codes=new HashSet<>();for(var patch:input){if(patch==null||!allowed.contains(patch.moduleCode())||!codes.add(patch.moduleCode())||(patch.isEnabled()==null&&patch.sortOrder()==null)||(patch.sortOrder()!=null&&(patch.sortOrder()<0||patch.sortOrder()>999)))throw new IllegalArgumentException("items.INVALID");var previous=byCode.get(patch.moduleCode());if(previous==null)throw new IllegalArgumentException("items.INVALID");byCode.put(patch.moduleCode(),new BrandSettings.Module(patch.moduleCode(),patch.isEnabled()==null?previous.isEnabled():patch.isEnabled(),patch.sortOrder()==null?previous.sortOrder():patch.sortOrder()));}var out=new ArrayList<>(byCode.values());out.sort(Comparator.comparingInt(BrandSettings.Module::sortOrder).thenComparing(BrandSettings.Module::moduleCode));return out;}
    private static List<AuditEvent.BrandColor> toAuditColors(List<BrandSettings.Color> values){return values.stream().map(v->new AuditEvent.BrandColor(v.colorHex(),v.sortOrder())).toList();}
    private static List<AuditEvent.ModuleState> toAuditModules(List<BrandSettings.Module> values){return values.stream().map(v->new AuditEvent.ModuleState(v.moduleCode(),v.isEnabled(),v.sortOrder())).toList();}
    private static String orDefault(String value,String fallback) { return value == null ? fallback : value; }
    public record Command(UUID actor, UUID organizationId, boolean platformAdmin, int rowVersion, String key, String operation, String fingerprint, String requestId, String leaseOwner, Instant leaseExpiry, Instant retentionExpiry) {}
    private record Claim(IdempotencyOutcome.Claimed claim, IdempotencyOutcome.IdempotencyResult replayResult, boolean replay) {}
}
