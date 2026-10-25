package com.feng.dsagent.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.RestController;

/**
 * Repo-wide layering rules. Keep in sync with {@code docs/architecture/backend-standards.md}.
 */
class BackendArchitectureTest {

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
    public void servicesMustNotUseJdbc() {
        noClasses()
            .that().haveSimpleNameEndingWith("Service")
            .and().resideInAPackage("com.feng.dsagent..")
            .should().dependOnClassesThat(belongToAnyOf(JdbcTemplate.class, NamedParameterJdbcTemplate.class))
            .because("Services use MyBatis Mappers, not JdbcTemplate")
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
    public void architectureRulesDetectForbiddenControllerJdbc() {
        var classes = new ClassFileImporter().importClasses(ForbiddenJdbcController.class);
        assertThatThrownBy(() -> noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat(belongToAnyOf(JdbcTemplate.class))
            .check(classes))
            .isInstanceOf(AssertionError.class);
    }

    @RestController
    static class ForbiddenJdbcController {
        @SuppressWarnings("unused")
        JdbcTemplate jdbc;
    }
}
