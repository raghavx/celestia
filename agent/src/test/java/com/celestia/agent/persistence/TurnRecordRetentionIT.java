package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.llm.SourceRef;
import com.celestia.agent.llm.ToolCallRecord;
import com.celestia.agent.llm.TurnDetail;
import com.celestia.agent.persistence.repository.OwnerTurnRecordRepository;
import com.celestia.agent.persistence.store.AppUserRef;
import com.celestia.agent.persistence.store.TurnRecordStore;
import com.celestia.agent.persistence.store.UserAccountService;
import com.celestia.core.toolkit.Sensitivity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * SPEC-012 FR-020a, FR-020b and SC-013 — the turn record holds personal data, so
 * it is deleted.
 *
 * <p>Two rules, and the second must not be mistaken for a way round the first:
 * "forget me" removes every record for a user, and age purges records past the
 * retention. <b>A retention of {@code 0} means never purge on age</b> — it is a
 * convenience, not a way to switch off deletion, and that distinction is what
 * this test pins.
 *
 * <p>Named {@code …IT} rather than {@code …Test} because it needs a real MySQL
 * (ADR-0008): a {@code Test} that silently requires Docker breaks the default
 * build on any machine without it.
 *
 * <p>The purge takes its "now" from an injected {@link java.time.Clock}, so
 * "a record past the retention age is gone" is testable without waiting ninety
 * days — this drives the clock by writing records with old timestamps.
 */
class TurnRecordRetentionIT extends PersistenceIT {

    @Autowired
    TurnRecordStore store;

    @Autowired
    OwnerTurnRecordRepository repo;

    @Autowired
    UserAccountService users;

    private AppUserRef user(String phone) {
        return users.findOrCreate(phone);
    }

    private void write(long userId, Instant receivedAt, String question) {
        store.record(new TurnDetail(
                userId,
                receivedAt,
                question,
                TurnDetail.Entitlement.ALLOWED,
                java.util.Optional.of("ALLOWED"),
                java.util.Optional.of("IN_SCOPE"),
                TurnDetail.Outcome.ANSWERED,
                List.of(new ToolCallRecord(1, "findToolkitRules", "{\"matter\":\"marriage\"}",
                        List.of("s1"), false)),
                Map.of("s1", new SourceRef("marriage-rules", "MarriageRules", 7)),
                Sensitivity.NONE,
                java.util.Optional.of("The 7th cusp sub lord connects 2, 7 and 11."),
                44,
                java.util.Optional.empty(),
                java.util.Optional.of("llama4:scout"),
                false,
                java.util.Optional.empty(),
                false));
    }

    @Test
    @DisplayName("a turn is stored with its question, its reply and its resolved sources")
    void aTurnIsRecordedForReview() {
        AppUserRef owner = user("+919000000001");
        write(owner.id(), Instant.now(), "when will I get married?");

        var stored = repo.findByUserIdOrderByReceivedAtAsc(owner.id());
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getQuestion()).isEqualTo("when will I get married?");
        assertThat(stored.getFirst().getDeliveredReply()).contains("7th cusp sub lord");
        assertThat(stored.getFirst().getModelUsed()).isEqualTo("llama4:scout");
        // The real source is resolvable here and nowhere the model can see.
        assertThat(stored.getFirst().getResolvedSourcesJson()).contains("marriage-rules");
        assertThat(stored.getFirst().getToolCallsJson()).contains("s1");
    }

    @Test
    @DisplayName("a \"forget me\" request leaves ZERO turn records for that user (SC-013)")
    void forgetMeLeavesNothing() {
        AppUserRef owner = user("+919000000002");
        write(owner.id(), Instant.now(), "when will I get married?");
        write(owner.id(), Instant.now(), "and what about my career?");
        assertThat(store.countForUser(owner.id())).isEqualTo(2);

        users.forget(owner.id());

        // A deletion that leaves every question a user ever asked is not a deletion.
        assertThat(store.countForUser(owner.id())).isZero();
    }

    @Test
    @DisplayName("a record past the retention age is gone on the next purge; one inside it survives")
    void thePurgeTakesOnlyWhatIsOldEnough() {
        AppUserRef owner = user("+919000000003");
        write(owner.id(), Instant.now().minus(Duration.ofDays(120)), "an old question");
        write(owner.id(), Instant.now().minus(Duration.ofDays(10)), "a recent question");

        long removed = store.purgeOlderThan(Duration.ofDays(90));

        assertThat(removed).isEqualTo(1);
        assertThat(repo.findByUserIdOrderByReceivedAtAsc(owner.id()))
                .singleElement()
                .satisfies(row -> assertThat(row.getQuestion()).isEqualTo("a recent question"));
    }

    @Test
    @DisplayName("retention 0 purges nothing on age — while forget-me still deletes everything")
    void zeroRetentionIsNotADeletionSwitch() {
        AppUserRef owner = user("+919000000004");
        write(owner.id(), Instant.now().minus(Duration.ofDays(3650)), "a very old question");

        assertThat(store.purgeOlderThan(Duration.ZERO)).isZero();
        assertThat(store.countForUser(owner.id())).isEqualTo(1);

        // The standing obligation is unaffected by the setting.
        users.forget(owner.id());
        assertThat(store.countForUser(owner.id())).isZero();
    }

    @Test
    @DisplayName("the default retention is ninety days")
    void theDefaultRetentionIsNinetyDays() {
        assertThat(store.retention()).isEqualTo(Duration.ofDays(90));
    }
}
