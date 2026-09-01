package com.celestia.agent.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Enforces Constitution principles II and IX.
 *
 * <p>The pure domain modules must not depend on Spring, and the domain core must
 * not depend on the edge adapters. The single-path-to-the-chat-model rule
 * (principle V) is added in SPEC-014 once the pipeline exists.
 */
class LayeringArchitectureTest {

    private static JavaClasses celestia;

    @BeforeAll
    static void importClasses() {
        celestia = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.celestia");
    }

    @Test
    void archTestsAreNotVacuous() {
        assertThat(celestia).as("no com.celestia classes were imported").isNotEmpty();
    }

    @Test
    void pureDomainModulesDoNotDependOnSpring() {
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
                .resideInAnyPackage("org.springframework..")
                .as("Constitution II: the domain core is pure — no Spring, no DB, no network")
                .allowEmptyShould(true)
                .check(celestia);
    }

    @Test
    void domainCoreDoesNotDependOnEdgeAdapters() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.celestia.core..", "com.celestia.ephemeris..", "com.celestia.geo..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.celestia.channel..", "com.celestia.payments..")
                .as("Constitution IX: adapters sit at the edge; the core never imports them")
                .allowEmptyShould(true)
                .check(celestia);
    }
}
