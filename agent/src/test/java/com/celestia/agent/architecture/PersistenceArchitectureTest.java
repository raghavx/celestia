package com.celestia.agent.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * SPEC-008 FR-014 / FR-015 — persistence stays in {@code agent}. The pure domain
 * modules gain no dependency on JPA, Spring Data, Hibernate, Flyway or JDBC; the
 * JPA classes are adapters behind the domain ports (e.g. {@code geo.GeocodeCache}).
 */
class PersistenceArchitectureTest {

    private static JavaClasses celestia;

    @BeforeAll
    static void importClasses() {
        celestia = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.celestia");
    }

    @Test
    void pureModulesDoNotDependOnPersistenceLibraries() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.celestia.ephemeris..",
                        "com.celestia.core..",
                        "com.celestia.geo..",
                        "com.celestia.billing..",
                        "com.celestia.guardrail..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "jakarta.persistence..",
                        "org.springframework.data..",
                        "org.hibernate..",
                        "org.flywaydb..",
                        "java.sql..",
                        "javax.sql..",
                        "com.zaxxer.hikari..")
                .as("SPEC-008: the pure domain modules must not depend on a persistence library")
                .allowEmptyShould(true)
                .check(celestia);
    }

    @Test
    void jpaEntitiesStayInThePersistencePackage() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.celestia.agent.persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.celestia.agent.persistence.entity..")
                .as("SPEC-008 FR-015: JPA entities are internal to the persistence layer")
                .allowEmptyShould(true)
                .check(celestia);
    }
}
