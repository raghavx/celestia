package com.celestia.agent.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Constitution II (deterministic pure core) and IX / FR-018 (adapter isolation).
 *
 * <ul>
 *   <li>(a) {@code ephemeris} and {@code core} must not read a wall clock.</li>
 *   <li>(b) the Swiss Ephemeris library type must never surface in the public API
 *       of {@code com.celestia.ephemeris} — it stays behind {@code PositionProvider}.</li>
 * </ul>
 */
class DeterminismArchitectureTest {

    private static final String[] PURE_PACKAGES = {
        "com.celestia.ephemeris..",
        "com.celestia.core..",
        "com.celestia.geo..",
        "com.celestia.billing..",
        "com.celestia.guardrail.."
    };

    private static final List<String> AMBIENT_CLOCK = List.of(
            "java.time.Instant.now(",
            "java.time.LocalDate.now(",
            "java.time.LocalDateTime.now(",
            "java.time.LocalTime.now(",
            "java.time.ZonedDateTime.now(",
            "java.time.OffsetDateTime.now(",
            "java.time.Year.now(",
            "java.time.YearMonth.now(",
            "java.time.Clock.systemUTC(",
            "java.time.Clock.systemDefaultZone(",
            "java.time.Clock.system(",
            "java.lang.System.currentTimeMillis(",
            "java.lang.System.nanoTime(",
            "java.util.Date.<init>()");

    private static JavaClasses celestia;

    @BeforeAll
    static void importClasses() {
        celestia = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.celestia");
    }

    @Test
    void pureModulesDoNotReadAWallClock() {
        DescribedPredicate<JavaCall<?>> ambientClockCall = new DescribedPredicate<>("an ambient clock / wall-clock API") {
            @Override
            public boolean test(JavaCall<?> call) {
                String target = call.getTarget().getFullName();
                return AMBIENT_CLOCK.stream().anyMatch(target::startsWith);
            }
        };

        noClasses()
                .that()
                .resideInAnyPackage(PURE_PACKAGES)
                .should()
                .callCodeUnitWhere(ambientClockCall)
                .as("Constitution II / FR-014: the pure modules must be deterministic - no wall clock")
                .allowEmptyShould(true)
                .check(celestia);
    }

    @Test
    void swissEphemerisTypesDoNotLeakFromEphemerisPublicApi() {
        DescribedPredicate<JavaClass> swissEph =
                DescribedPredicate.describe("a de.thmac.swisseph type", c -> c.getPackageName().startsWith("de.thmac.swisseph"));

        methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("com.celestia.ephemeris")
                .and()
                .arePublic()
                .should()
                .notHaveRawReturnType(swissEph)
                .andShould()
                .notHaveRawParameterTypes(new DescribedPredicate<>("include a de.thmac.swisseph type") {
                    @Override
                    public boolean test(List<JavaClass> params) {
                        return params.stream().anyMatch(swissEph);
                    }
                })
                .as("FR-018: no Swiss Ephemeris type in a public method of com.celestia.ephemeris")
                .allowEmptyShould(true)
                .check(celestia);

        fields()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("com.celestia.ephemeris")
                .and()
                .arePublic()
                .should()
                .notHaveRawType(swissEph)
                .as("FR-018: no Swiss Ephemeris type in a public field of com.celestia.ephemeris")
                .allowEmptyShould(true)
                .check(celestia);
    }
}
