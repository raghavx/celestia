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

    /**
     * Chat-model types, by package. SPEC-011 FR-003 requires that no code path
     * reach a chat model except through the guardrail, and that this be
     * mechanically enforced rather than trusted.
     */
    private static final String[] CHAT_MODEL_PACKAGES = {
        "org.springframework.ai..",
        "dev.langchain4j..",
        "io.github.ollama4j..",
        "com.openai..",
        "com.anthropic.."
    };

    /** The only types permitted to invoke one (SPEC-012 T018, research.md §2). */
    private static final String GUARDED_PIPELINE = "com.celestia.guardrail.GuardedPipeline";

    private static final String OWNER_ANSWERER = "com.celestia.agent.llm.OwnerAnswerer";

    /**
     * Where a chat model is actually <em>invoked</em>. Inference, not import: the
     * call that sends a prompt and gets a completion back.
     */
    private static final List<String> INFERENCE_CALLS = List.of(
            "org.springframework.ai.chat.model.ChatModel.call(",
            "org.springframework.ai.chat.model.ChatModel.stream(",
            "org.springframework.ai.ollama.OllamaChatModel.call(",
            "org.springframework.ai.ollama.OllamaChatModel.stream(",
            "org.springframework.ai.chat.client.ChatClient.prompt(");

    /** The package Spring AI types may not escape (rule b). */
    private static final String LLM_PACKAGE = "com.celestia.agent.llm..";

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

    @Test
    void timeshapeTypesDoNotLeakFromGeoPublicApi() {
        DescribedPredicate<JavaClass> timeshape = DescribedPredicate.describe(
                "a net.iakovlev (timeshape) type",
                c -> c.getPackageName().startsWith("net.iakovlev"));

        methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("com.celestia.geo..")
                .and()
                .arePublic()
                .should()
                .notHaveRawReturnType(timeshape)
                .andShould()
                .notHaveRawParameterTypes(new DescribedPredicate<>("include a net.iakovlev type") {
                    @Override
                    public boolean test(List<JavaClass> params) {
                        return params.stream().anyMatch(timeshape);
                    }
                })
                .as("SPEC-007 FR-011: timeshape stays behind TimeZoneResolver — not in the geo public API")
                .allowEmptyShould(true)
                .check(celestia);

        fields()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("com.celestia.geo..")
                .and()
                .arePublic()
                .should()
                .notHaveRawType(timeshape)
                .as("SPEC-007 FR-011: no timeshape type in a public field of com.celestia.geo")
                .allowEmptyShould(true)
                .check(celestia);
    }

    /**
     * Constitution V (NON-NEGOTIABLE), rule (a): there is exactly one route from a
     * user message to an answer, and only two classes stand on it —
     * {@code GuardedPipeline}, which calls the answerer, and
     * {@code OwnerAnswerer}, which calls the model.
     *
     * <p><b>Inference, not import.</b> SPEC-011 wrote this as "no class may
     * depend on a chat-model type", which was exact while no model existed and
     * became impossible the moment one did: {@code LlmConfig} cannot construct a
     * {@code ChatClient} without naming {@code OllamaChatModel}, and the
     * {@code @Tool} adapters carry a Spring AI annotation, which ArchUnit counts
     * as a dependency. Naming the <em>call</em> keeps the guarantee that matters —
     * only these two can send a prompt — without a rule that is red on the day the
     * feature is written. Containment is rule (b), below.
     */
    @Test
    void onlyTwoClassesMayInvokeAChatModel() {
        DescribedPredicate<JavaCall<?>> inferenceCall = new DescribedPredicate<>("a chat-model invocation") {
            @Override
            public boolean test(JavaCall<?> call) {
                String target = call.getTarget().getFullName();
                return INFERENCE_CALLS.stream().anyMatch(target::startsWith);
            }
        };

        noClasses()
                .that()
                .resideInAPackage("com.celestia..")
                .and()
                .doNotHaveFullyQualifiedName(GUARDED_PIPELINE)
                .and()
                .doNotHaveFullyQualifiedName(OWNER_ANSWERER)
                .should()
                .callCodeUnitWhere(inferenceCall)
                .as("Constitution V: only GuardedPipeline and OwnerAnswerer may invoke a chat model")
                .allowEmptyShould(true)
                .check(celestia);
    }

    /**
     * Constitution V (NON-NEGOTIABLE), rule (b): Spring AI stays in
     * {@code com.celestia.agent.llm}.
     *
     * <p>Rule (a) says who may call the model; this says where the model's types
     * may be named at all, so no controller, worker, handler or domain class can
     * hold one — which is how a second path would start. Together they are tighter
     * than the single rule they replace, because that one could only ever have
     * been satisfied by widening it to a package.
     *
     * <p>{@code GuardedPipeline} is exempt: it is the guardrail's own seam and
     * lives outside {@code agent}.
     */
    @Test
    void springAiStaysInTheLlmPackage() {
        noClasses()
                .that()
                .resideInAPackage("com.celestia..")
                .and()
                .resideOutsideOfPackage(LLM_PACKAGE)
                .and()
                .doNotHaveFullyQualifiedName(GUARDED_PIPELINE)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(CHAT_MODEL_PACKAGES)
                .as("Constitution V: a chat-model type may only be named inside com.celestia.agent.llm")
                .allowEmptyShould(true)
                .check(celestia);
    }
}
