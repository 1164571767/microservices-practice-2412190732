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
  void domainAndApplicationDoNotDependOnWebOrInfrastructure() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..web..", "..infrastructure..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void restControllersResideInWebPackage() {
    ArchRule rule =
        classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .resideInAPackage("..web..");
    rule.check(classes);
  }
}
