package com.celestia.agent.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * SPEC-009 FR-011 — the Graph API request/response DTOs stay internal to
 * {@code com.celestia.channel.whatsapp.graphapi}. {@code agent} orchestration
 * code (and everything else) holds only {@code MessagingChannel},
 * {@code OutboundMessage}, {@code SendResult}, and the other public
 * {@code com.celestia.channel.whatsapp} types.
 */
class ChannelArchitectureTest {

    private static JavaClasses celestia;

    @BeforeAll
    static void importClasses() {
        celestia = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.celestia");
    }

    @Test
    void graphApiDtosStayInternalToTheWhatsappAdapter() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.celestia.channel.whatsapp..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.celestia.channel.whatsapp.graphapi..")
                .as("SPEC-009 FR-011: Graph API DTOs are internal to channel-whatsapp; "
                        + "agent code depends only on MessagingChannel / OutboundMessage / SendResult")
                .allowEmptyShould(true)
                .check(celestia);
    }
}
