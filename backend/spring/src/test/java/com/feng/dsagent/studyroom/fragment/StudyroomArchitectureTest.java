package com.feng.dsagent.studyroom.fragment;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.*;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class StudyroomArchitectureTest {
    @Test void planCompilerCannotCallModelsOrWriteTheDatabase() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.feng.dsagent.studyroom.fragment");
        assertThat(classes).anyMatch(c -> c.getSimpleName().equals("ContentPlanCompiler"));
        noClasses().that().haveSimpleName("ContentPlanCompiler").should()
            .dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "com.feng.dsagent.model..")
            .check(classes);
    }

    @Test void architectureRulesDetectActualForbiddenBytecodeDependencies() {
        var classes = new ClassFileImporter().importClasses(ForbiddenComposer.class);
        assertThatThrownBy(() -> noClasses().that().haveSimpleName("ForbiddenComposer")
            .should().dependOnClassesThat().haveFullyQualifiedName(JdbcTemplate.class.getName()).check(classes))
            .isInstanceOf(AssertionError.class);
    }

    static class ForbiddenComposer { JdbcTemplate database; }
}
