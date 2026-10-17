package com.zjgsu.sfy;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureTest {

  static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.zjgsu.sfy");
  }

  @Test
  void innerLayersDoNotDependOnControllerOrConfig() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAnyPackage("..entity..", "..repository..", "..service..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..config..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void restControllersResideInControllerPackage() {
    ArchRule rule =
        classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .resideInAPackage("..controller..");
    rule.check(classes);
  }

  @Test
  void identityInternalsDoNotLeakToOtherModules() {
    ArchRule rule =
        noClasses()
            .that()
            .resideOutsideOfPackage("..identity..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..identity.common..")
            .allowEmptyShould(true);
    rule.check(classes);
  }
}
