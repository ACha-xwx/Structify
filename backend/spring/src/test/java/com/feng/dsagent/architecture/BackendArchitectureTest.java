package com.feng.dsagent.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.RestController;

/**
 * Repo-wide layering rules. Keep in sync with {@code docs/architecture/backend-standards.md}.
 */
class BackendArchitectureTest {

    private static final String[] CAPABILITIES = {
        "admin",
        "ai",
        "aiquota",
        "animation",
        "auth",
        "chat",
        "classroom",
        "compiler",
        "knowledge",
        "learning",
        "mail",
        "modelconfig",
        "presentation",
        "resource",
        "review",
        "security",
        "studyroom",
        "teacher"
    };

    private static final String[] STUDYROOM_SUBDOMAINS = {
        "checks",
        "curriculum",
        "draft",
        "events",
        "fragment",
        "generation",
        "library",
        "questions",
        "teaching"
    };

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.feng.dsagent");
    }

    @Test
    public void controllersMustNotUseJdbc() {
        noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat(belongToAnyOf(JdbcTemplate.class, NamedParameterJdbcTemplate.class))
            .because("Controllers must not touch JDBC; see backend-standards.md")
            .check(productionClasses);
    }

    @Test
    public void controllersMustNotDependOnMyBatisMappers() {
        noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat(
                resideInAnyPackage("..mapper..").or(resideInAnyPackage("org.apache.ibatis.."))
            )
            .because("Controllers call Services, not Mappers")
            .check(productionClasses);
    }

    @Test
    public void controllersMustNotDependOnModelPackage() {
        noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().resideInAPackage("com.feng.dsagent.model..")
            .because("Model clients belong behind Services; rate limits live in common")
            .check(productionClasses);
    }

    @Test
    public void controllersMustNotDependOnEntityOrQueryPackages() {
        noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().resideInAnyPackage("..entity..", "..query..")
            .because("Controllers must not depend on entity/query packages; see backend-standards.md")
            .check(productionClasses);
    }

    @Test
    public void servicesMustNotUseJdbc() {
        noClasses()
            .that().haveSimpleNameEndingWith("Service")
            .and().resideInAPackage("com.feng.dsagent..")
            .should().dependOnClassesThat(belongToAnyOf(JdbcTemplate.class, NamedParameterJdbcTemplate.class))
            .because("Services use MyBatis Mappers, not JdbcTemplate")
            .check(productionClasses);
    }

    @Test
    public void servicesMustNotDependOnControllersOrServletRequest() {
        noClasses()
            .that().haveSimpleNameEndingWith("Service")
            .and().resideInAPackage("com.feng.dsagent..")
            .should().dependOnClassesThat(
                resideInAnyPackage("..controller..")
                    .or(resideInAnyPackage("jakarta.servlet..", "javax.servlet.."))
            )
            .because("Services must not depend on Controllers or servlet request types")
            .check(productionClasses);
    }

    @Test
    public void workersMustNotDependOnControllers() {
        noClasses()
            .that().resideInAPackage("..worker..")
            .or().haveSimpleNameEndingWith("Worker")
            .or().haveSimpleNameEndingWith("Runner")
            .or().haveSimpleNameEndingWith("Scheduler")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .because("Workers call Services, not Controllers")
            .check(productionClasses);
    }

    @Test
    public void dtoAndVoMustNotDependOnPersistence() {
        noClasses()
            .that().resideInAnyPackage("..dto..", "..vo..")
            .should().dependOnClassesThat(
                resideInAnyPackage("..mapper..", "..entity..", "..query..")
                    .or(belongToAnyOf(JdbcTemplate.class, NamedParameterJdbcTemplate.class))
            )
            .because("dto/vo must not depend on mapper, JDBC, entity, or query")
            .check(productionClasses);
    }

    @Test
    public void mappersMustNotDependOnDtoOrVo() {
        noClasses()
            .that().resideInAPackage("..mapper..")
            .should().dependOnClassesThat().resideInAnyPackage("..dto..", "..vo..")
            .because("Mappers use Entity/Query/simple params only")
            .check(productionClasses);
    }

    @Test
    public void domainMustNotDependOnWebMyBatisOrTransportShapes() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..dto..",
                "..vo..",
                "..entity..",
                "..query..",
                "..mapper..",
                "org.springframework.web..",
                "org.mybatis..",
                "org.apache.ibatis.."
            )
            .because("domain must stay free of Web/MyBatis/DTO/VO/PO")
            .check(productionClasses);
    }

    @Test
    public void configurationClassesMustResideInConfigPackages() {
        classes()
            .that().areAnnotatedWith(Configuration.class)
            .or().areAnnotatedWith(ConfigurationProperties.class)
            .and().areNotAnnotatedWith(SpringBootApplication.class)
            .should().resideInAnyPackage("..config..", "com.feng.dsagent.config..")
            .because("@Configuration / @ConfigurationProperties must live in config packages")
            .check(productionClasses);
    }

    @Test
    public void exceptionClassesMustResideInExceptionPackages() {
        classes()
            .that().haveSimpleNameEndingWith("Exception")
            .and().areTopLevelClasses()
            .should().resideInAnyPackage(
                "..exception..",
                "com.feng.dsagent.common.exception..",
                "com.feng.dsagent.model.exception..",
                "com.feng.dsagent.model.."
            )
            .because("Production *Exception classes must live in exception packages")
            .check(productionClasses);
    }

    @Test
    public void productionCodeMustNotDependOnJpa() {
        noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "javax.persistence..", "org.springframework.data.jpa..")
            .because("Persistence is JDBC + MyBatis, not JPA")
            .check(productionClasses);
    }

    @Test
    public void productionCodeMustNotDependOnApiException() {
        noClasses()
            .should().dependOnClassesThat().haveSimpleName("ApiException")
            .because("ApiException is removed; use BizException")
            .allowEmptyShould(true)
            .check(productionClasses);

        assertThat(productionClasses.stream()
            .filter(c -> "ApiException".equals(c.getSimpleName()))
            .map(JavaClass::getName)
            .toList())
            .as("ApiException must not exist in production")
            .isEmpty();
    }

    @Test
    public void productionCodeMustNotUseRepositoryTypesOrPackages() {
        noClasses()
            .that().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage("com.feng.dsagent..")
            .because("No *Repository persistence types; use MyBatis mappers")
            .allowEmptyShould(true)
            .check(productionClasses);

        noClasses()
            .that().resideInAPackage("..repository..")
            .should().beTopLevelClasses()
            .because("No repository leaf packages")
            .allowEmptyShould(true)
            .check(productionClasses);

        assertThat(productionClasses.stream()
            .filter(c -> c.getSimpleName().endsWith("Repository"))
            .map(JavaClass::getName)
            .toList()).isEmpty();
    }

    @Test
    public void capabilityAndStudyroomRootsMustNotHoldProductionTypes() {
        classes()
            .that(resideInExactCapabilityOrSubdomainRoot())
            .should().resideInAnyPackage("com.feng.dsagent.common..")
            .because("Capability/studyroom subdomain roots must not hold production types; use forced leaf packages")
            .allowEmptyShould(true)
            .check(productionClasses);

        List<String> offenders = productionClasses.stream()
            .filter(c -> isCapabilityOrSubdomainRoot(c.getPackageName()))
            .map(JavaClass::getName)
            .sorted()
            .toList();
        assertThat(offenders)
            .as("production types in capability/subdomain roots")
            .isEmpty();
    }

    @Test
    public void mapperXmlMustLiveUnderResourcesMapper() throws IOException {
        Path javaTree = Path.of("src/main/java");
        Path resourcesMapper = Path.of("src/main/resources/mapper");
        assertThat(Files.isDirectory(resourcesMapper)).isTrue();
        try (Stream<Path> walk = Files.walk(javaTree)) {
            List<Path> xmlInJava = walk
                .filter(p -> p.getFileName().toString().endsWith("Mapper.xml"))
                .toList();
            assertThat(xmlInJava).as("Mapper XML must not sit under src/main/java").isEmpty();
        }
    }

    @Test
    public void noWriteOrErrorCodesSimpleNames() {
        assertThat(productionClasses.stream()
            .filter(c -> c.getSimpleName().endsWith("Write") || c.getSimpleName().endsWith("ErrorCodes"))
            .map(JavaClass::getName)
            .toList()).isEmpty();
    }

    @Test
    public void dtoVoEntityMustUseForcedSuffixes() {
        classes()
            .that().resideInAPackage("..dto..")
            .and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("DTO")
            .because("dto leaf types must end with DTO")
            .check(productionClasses);

        classes()
            .that().resideInAPackage("..vo..")
            .and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("VO")
            .because("vo leaf types must end with VO")
            .check(productionClasses);

        classes()
            .that().resideInAPackage("..entity..")
            .and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Entity")
            .because("entity leaf types must end with Entity")
            .check(productionClasses);
    }

    @Test
    public void convertersMustResideInConverterPackageAndEndWithConverter() {
        // MapStruct generates *ConverterImpl; interfaces must still be *Converter.
        classes()
            .that().resideInAPackage("..converter..")
            .and().areTopLevelClasses()
            .and().areInterfaces()
            .should().haveSimpleNameEndingWith("Converter")
            .because("converter leaf interfaces must be named *Converter, never *Mapper")
            .allowEmptyShould(true)
            .check(productionClasses);

        // Abstract MapStruct mappers end with Converter; generated impls end with ConverterImpl.
        assertThat(productionClasses.stream()
            .filter(c -> c.getPackageName().contains(".converter"))
            .filter(JavaClass::isTopLevelClass)
            .filter(c -> !c.isInterface())
            .map(JavaClass::getSimpleName)
            .filter(name -> !(name.endsWith("Converter") || name.endsWith("ConverterImpl")))
            .sorted()
            .toList())
            .as("non-interface converter types must be *Converter or *ConverterImpl")
            .isEmpty();

        noClasses()
            .that().resideInAPackage("..converter..")
            .should().haveSimpleNameEndingWith("Mapper")
            .because("MapStruct converters must not be named *Mapper")
            .allowEmptyShould(true)
            .check(productionClasses);
    }

    @Test
    public void productionCodeMustNotUseBeanUtils() {
        assertThat(productionClasses.stream()
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .map(d -> d.getTargetClass().getName())
            .filter(n -> n.equals("org.springframework.beans.BeanUtils")
                || n.equals("org.apache.commons.beanutils.BeanUtils")
                || n.equals("org.apache.commons.beanutils.BeanUtilsBean"))
            .distinct()
            .toList())
            .as("BeanUtils reflection copy is forbidden; use MapStruct *Converter")
            .isEmpty();
    }

    @Test
    public void voMustNotExposePasswordHash() {
        List<String> offenders = productionClasses.stream()
            .filter(c -> c.getPackageName().contains(".vo"))
            .filter(c -> c.getAllFields().stream()
                .anyMatch(f -> {
                    String name = f.getName();
                    return name.equalsIgnoreCase("passwordHash")
                        || name.equalsIgnoreCase("password_hash");
                }))
            .map(JavaClass::getName)
            .sorted()
            .toList();
        assertThat(offenders).as("VO must not expose passwordHash").isEmpty();
    }

    @Test
    public void productionCodeMustNotCallPrintStackTrace() {
        assertThat(productionClasses.stream()
            .flatMap(c -> c.getMethodCallsFromSelf().stream())
            .filter(call -> "printStackTrace".equals(call.getName()))
            .map(call -> call.getOriginOwner().getName())
            .distinct()
            .toList())
            .as("use SLF4J instead of printStackTrace")
            .isEmpty();
    }

    @Test
    public void architectureRulesDetectForbiddenControllerJdbc() {
        var classes = new ClassFileImporter().importClasses(ForbiddenJdbcController.class);
        assertThatThrownBy(() -> noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat(belongToAnyOf(JdbcTemplate.class))
            .check(classes))
            .isInstanceOf(AssertionError.class);
    }

    private static DescribedPredicate<JavaClass> resideInExactCapabilityOrSubdomainRoot() {
        return new DescribedPredicate<>("reside in capability or studyroom subdomain root package") {
            @Override
            public boolean test(JavaClass javaClass) {
                return isCapabilityOrSubdomainRoot(javaClass.getPackageName());
            }
        };
    }

    private static boolean isCapabilityOrSubdomainRoot(String packageName) {
        for (String capability : CAPABILITIES) {
            if (("com.feng.dsagent." + capability).equals(packageName)) {
                return true;
            }
        }
        for (String subdomain : STUDYROOM_SUBDOMAINS) {
            if (("com.feng.dsagent.studyroom." + subdomain).equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    @RestController
    static class ForbiddenJdbcController {
        @SuppressWarnings("unused")
        JdbcTemplate jdbc;
    }
}
