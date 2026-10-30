package com.zjgsu.sfy.integration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;

class DataOwnershipTest {
  @Test
  void persistentEntitiesBelongToThisService() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.zjgsu.sfy");
    classes()
        .that()
        .areAnnotatedWith(jakarta.persistence.Entity.class)
        .should()
        .resideInAnyPackage("..appointment..", "..medical..", "..common.idempotency..")
        .check(classes);
    noClasses()
        .should()
        .resideInAnyPackage(
            "..identity.entity..", "..identity.repository..", "..identity.service..")
        .check(classes);
    noClasses()
        .should()
        .resideInAnyPackage("..owner.entity..", "..owner.repository..", "..owner.service..")
        .check(classes);
    noClasses()
        .should()
        .resideInAnyPackage(
            "..pharmacy.entity..", "..pharmacy.repository..", "..pharmacy.service..")
        .check(classes);
    noClasses()
        .should()
        .resideInAnyPackage("..billing.entity..", "..billing.repository..", "..billing.service..")
        .check(classes);
    noClasses()
        .should()
        .resideInAnyPackage(
            "..notification.entity..", "..notification.repository..", "..notification.service..")
        .check(classes);
    noClasses()
        .should()
        .resideInAnyPackage(
            "..reporting.entity..", "..reporting.repository..", "..reporting.service..")
        .check(classes);
  }
}
