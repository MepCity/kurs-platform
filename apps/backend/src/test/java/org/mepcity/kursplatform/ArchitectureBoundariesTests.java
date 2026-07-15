package org.mepcity.kursplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureBoundariesTests {

	private static final Path SOURCE_ROOT = Path.of("src/main/java/org/mepcity/kursplatform");
	private static final String APPLICATION_ROOT = "KursPlatformBackendApplication.java";
	private static final Set<String> BUSINESS_MODULES = Set.of(
			"iam", "org", "term", "cls", "people", "att", "content", "program",
			"progress", "audit", "export", "sync", "realtime", "notify");
	private static final Set<String> LAYERS = Set.of("api", "application", "domain", "infrastructure");
	private static final Pattern PROJECT_REFERENCE = Pattern.compile(
			"\\borg\\.mepcity\\.kursplatform\\.([a-z][a-z0-9]*)(?:\\.([a-z][a-z0-9_]*))?(?:\\.([a-z][a-z0-9_]*))?");
	private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^package\\s+([^;]+);");
	private static final List<String> DOMAIN_FRAMEWORK_PREFIXES = List.of(
			"org.springframework.", "jakarta.persistence.", "jakarta.servlet.", "java.net.http.");

	@Test
	void requiredModuleBoundariesAreVisible() {
		assertThat(SOURCE_ROOT.resolve("configuration/package-info.java")).exists();
		assertThat(SOURCE_ROOT.resolve("core/package-info.java")).exists();
		for (String module : BUSINESS_MODULES) {
			assertThat(SOURCE_ROOT.resolve(module).resolve("package-info.java"))
					.as("%s module boundary", module)
					.exists();
		}
	}

	@Test
	void productionSourcesRespectPackageAndDependencyWhitelists() throws IOException {
		try (Stream<Path> sources = Files.walk(SOURCE_ROOT)) {
			List<String> violations = sources
					.filter(path -> path.toString().endsWith(".java"))
					.flatMap(this::violations)
					.toList();

			assertThat(violations).isEmpty();
		}
	}

	@Test
	void rejectsUnknownBusinessModule() {
		assertForbidden("billing/domain/Invoice.java", "class Invoice {}");
	}

	@Test
	void rejectsUnknownLayerUnderKnownModule() {
		assertForbidden("iam/service/LoginService.java", "class LoginService {}");
	}

	@Test
	void rejectsUndefinedTopLevelPackage() {
		assertForbidden("shared/Helper.java", "class Helper {}");
	}

	@Test
	void rejectsCoreToBusinessModule() {
		assertForbidden("core/Clock.java", "import org.mepcity.kursplatform.iam.domain.User;");
	}

	@Test
	void rejectsBusinessModuleToConfiguration() {
		assertForbidden("iam/application/UseCase.java", "import org.mepcity.kursplatform.configuration.Wiring;");
	}

	@Test
	void rejectsUnknownModuleThroughStaticImport() {
		assertForbidden(
				"iam/application/UseCase.java",
				"import static org.mepcity.kursplatform.billing.application.InvoiceService.create;");
	}

	@Test
	void rejectsUnknownLayerThroughFullyQualifiedReference() {
		assertForbidden(
				"iam/application/UseCase.java",
				"class UseCase { org.mepcity.kursplatform.iam.repository.UserStore store; }");
	}

	@Test
	void rejectsPackageDeclarationThatDoesNotMatchSourcePath() {
		assertForbidden(
				"iam/domain/Rule.java",
				"package org.mepcity.kursplatform.iam.service; class Rule {}");
	}

	@Test
	void rejectsDomainToApi() {
		assertForbidden("iam/domain/Rule.java", "import org.mepcity.kursplatform.iam.api.Endpoint;");
	}

	@Test
	void rejectsDomainToApplication() {
		assertForbidden("iam/domain/Rule.java", "import org.mepcity.kursplatform.iam.application.UseCase;");
	}

	@Test
	void rejectsDomainToInfrastructure() {
		assertForbidden("iam/domain/Rule.java", "import org.mepcity.kursplatform.iam.infrastructure.Store;");
	}

	@Test
	void rejectsApplicationToApi() {
		assertForbidden("iam/application/UseCase.java", "import org.mepcity.kursplatform.iam.api.Endpoint;");
	}

	@Test
	void rejectsApplicationToInfrastructure() {
		assertForbidden("iam/application/UseCase.java", "import org.mepcity.kursplatform.iam.infrastructure.Store;");
	}

	@Test
	void rejectsApiToInfrastructure() {
		assertForbidden("iam/api/Endpoint.java", "import org.mepcity.kursplatform.iam.infrastructure.Store;");
	}

	@Test
	void rejectsInfrastructureToApi() {
		assertForbidden("iam/infrastructure/Adapter.java", "import org.mepcity.kursplatform.iam.api.Endpoint;");
	}

	@Test
	void rejectsStaticImportThatBreaksLayerDirection() {
		assertForbidden(
				"iam/domain/Rule.java",
				"import static org.mepcity.kursplatform.iam.application.UseCase.execute;");
	}

	@Test
	void rejectsFullyQualifiedReferenceThatBreaksLayerDirection() {
		assertForbidden(
				"iam/application/UseCase.java",
				"class UseCase { org.mepcity.kursplatform.iam.infrastructure.Store store; }");
	}

	@Test
	void acceptsApplicationToPublishedCrossModuleContract() {
		assertThat(violations(
				"iam/application/UseCase.java",
				"import org.mepcity.kursplatform.org.application.contract.OrganizationReader;"))
				.isEmpty();
	}

	@Test
	void acceptsConfigurationToPublishedCrossModuleContract() {
		assertThat(violations(
				"configuration/Wiring.java",
				"import org.mepcity.kursplatform.org.application.contract.OrganizationReader;"))
				.isEmpty();
	}

	@Test
	void rejectsDomainToPublishedCrossModuleContract() {
		assertForbidden(
				"iam/domain/Rule.java",
				"import org.mepcity.kursplatform.org.application.contract.OrganizationReader;");
	}

	@Test
	void rejectsApiToPublishedCrossModuleContract() {
		assertForbidden(
				"iam/api/Endpoint.java",
				"import org.mepcity.kursplatform.org.application.contract.OrganizationReader;");
	}

	@Test
	void rejectsInfrastructureToPublishedCrossModuleContract() {
		assertForbidden(
				"iam/infrastructure/Adapter.java",
				"import org.mepcity.kursplatform.org.application.contract.OrganizationReader;");
	}

	@Test
	void rejectsFullyQualifiedDomainToPublishedCrossModuleContract() {
		assertForbidden(
				"iam/domain/Rule.java",
				"class Rule { org.mepcity.kursplatform.org.application.contract.OrganizationReader reader; }");
	}

	@Test
	void rejectsStaticApiToPublishedCrossModuleContract() {
		assertForbidden(
				"iam/api/Endpoint.java",
				"import static org.mepcity.kursplatform.org.application.contract.OrganizationReader.read;");
	}

	@Test
	void rejectsUnpublishedCrossModuleApplicationType() {
		assertForbidden(
				"iam/application/UseCase.java",
				"import org.mepcity.kursplatform.org.application.InternalService;");
	}

	@Test
	void rejectsFullyQualifiedCrossModuleInfrastructureReference() {
		assertForbidden(
				"iam/application/UseCase.java",
				"class UseCase { org.mepcity.kursplatform.org.infrastructure.Repository repository; }");
	}

	@Test
	void rejectsDomainFrameworkReferenceWithoutImport() {
		assertForbidden(
				"iam/domain/Rule.java",
				"class Rule { org.springframework.http.HttpStatus status; }");
	}

	private void assertForbidden(String path, String source) {
		assertThat(violations(path, source)).as(path).isNotEmpty();
	}

	private Stream<String> violations(Path source) {
		try {
			return violations(SOURCE_ROOT.relativize(source).toString(), Files.readString(source)).stream();
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot inspect " + source, exception);
		}
	}

	private List<String> violations(String relativePath, String source) {
		String path = relativePath.replace('\\', '/');
		SourceLocation location = locate(path);
		List<String> violations = new ArrayList<>();
		if (location.kind() == SourceKind.INVALID) {
			violations.add(path + ": source package is outside the whitelist");
		}
		Matcher packageMatcher = PACKAGE_DECLARATION.matcher(source);
		if (packageMatcher.find() && !expectedPackage(path).equals(packageMatcher.group(1))) {
			violations.add(path + ": declared package does not match source path");
		}

		if (location.kind() == SourceKind.BUSINESS && "domain".equals(location.layer())) {
			for (String prefix : DOMAIN_FRAMEWORK_PREFIXES) {
				if (source.contains(prefix)) {
					violations.add(path + ": domain framework reference: " + prefix);
				}
			}
		}

		Matcher matcher = PROJECT_REFERENCE.matcher(withoutPackageDeclaration(source));
		while (matcher.find()) {
			String targetTopLevel = matcher.group(1);
			String targetLayer = matcher.group(2);
			String targetSubpackage = matcher.group(3);
			inspectReference(location, targetTopLevel, targetLayer, targetSubpackage, matcher.group(), violations);
		}
		return violations;
	}

	private SourceLocation locate(String path) {
		if (APPLICATION_ROOT.equals(path)) {
			return new SourceLocation(SourceKind.ROOT, null, null);
		}
		String[] parts = path.split("/");
		if (parts.length < 2) {
			return SourceLocation.invalid();
		}
		if ("configuration".equals(parts[0])) {
			return new SourceLocation(SourceKind.CONFIGURATION, null, null);
		}
		if ("core".equals(parts[0])) {
			return new SourceLocation(SourceKind.CORE, null, null);
		}
		if (!BUSINESS_MODULES.contains(parts[0])) {
			return SourceLocation.invalid();
		}
		if (parts.length == 2 && "package-info.java".equals(parts[1])) {
			return new SourceLocation(SourceKind.MODULE_MARKER, parts[0], null);
		}
		if (parts.length < 3 || !LAYERS.contains(parts[1])) {
			return SourceLocation.invalid();
		}
		return new SourceLocation(SourceKind.BUSINESS, parts[0], parts[1]);
	}

	private String expectedPackage(String path) {
		int separator = path.lastIndexOf('/');
		String relativePackage = separator < 0 ? "" : path.substring(0, separator).replace('/', '.');
		return relativePackage.isEmpty()
				? "org.mepcity.kursplatform"
				: "org.mepcity.kursplatform." + relativePackage;
	}

	private void inspectReference(
			SourceLocation source,
			String targetTopLevel,
			String targetLayer,
			String targetSubpackage,
			String reference,
			List<String> violations) {
		if (BUSINESS_MODULES.contains(targetTopLevel)) {
			if (!LAYERS.contains(targetLayer)) {
				violations.add("unknown target layer: " + reference);
				return;
			}
			if (source.kind() == SourceKind.CORE) {
				violations.add("core cannot reference a business module: " + reference);
				return;
			}
			if (source.kind() == SourceKind.BUSINESS && source.module().equals(targetTopLevel)) {
				if (!isAllowedSameModuleReference(source.layer(), targetLayer)) {
					violations.add("forbidden same-module layer reference: " + reference);
				}
				return;
			}
			if (source.kind() == SourceKind.CONFIGURATION) {
				return;
			}
			boolean publishedContract = "application".equals(targetLayer) && "contract".equals(targetSubpackage);
			if (!(source.kind() == SourceKind.BUSINESS
					&& "application".equals(source.layer())
					&& publishedContract)) {
				violations.add("cross-module reference is not allowed from this source layer: " + reference);
			}
			return;
		}

		if ("configuration".equals(targetTopLevel)) {
			if (source.kind() == SourceKind.BUSINESS || source.kind() == SourceKind.CORE) {
				violations.add("configuration is composition-root only: " + reference);
			}
			return;
		}
		if ("core".equals(targetTopLevel)) {
			return;
		}
		violations.add("unknown target top-level package: " + reference);
	}

	private boolean isAllowedSameModuleReference(String sourceLayer, String targetLayer) {
		if (sourceLayer.equals(targetLayer)) {
			return true;
		}
		return switch (sourceLayer) {
			case "api" -> Set.of("application", "domain").contains(targetLayer);
			case "application" -> "domain".equals(targetLayer);
			case "domain" -> false;
			case "infrastructure" -> Set.of("application", "domain").contains(targetLayer);
			default -> false;
		};
	}

	private String withoutPackageDeclaration(String source) {
		return source.replaceFirst("(?m)^package\\s+[^;]+;", "");
	}

	private enum SourceKind {
		ROOT,
		CONFIGURATION,
		CORE,
		MODULE_MARKER,
		BUSINESS,
		INVALID
	}

	private record SourceLocation(SourceKind kind, String module, String layer) {
		private static SourceLocation invalid() {
			return new SourceLocation(SourceKind.INVALID, null, null);
		}
	}
}
