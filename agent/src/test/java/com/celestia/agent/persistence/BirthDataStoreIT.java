package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.persistence.repository.BirthDataRepository;
import com.celestia.agent.persistence.store.BirthDataStore;
import com.celestia.agent.persistence.store.StoredBirth;
import com.celestia.agent.persistence.store.UserAccountService;
import com.celestia.geo.ResolvedBirth;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/** SPEC-008 US1 / SC-001 — a birth round-trips losslessly. */
@Transactional
class BirthDataStoreIT extends PersistenceIT {

    @Autowired
    BirthDataStore store;

    @Autowired
    UserAccountService users;

    @Autowired
    BirthDataRepository repo;

    private long newUser(String phone) {
        return users.findOrCreate(phone).id();
    }

    @Test
    void savesAndReloadsEveryFieldExactly() {
        long userId = newUser("+911111100001");
        ResolvedBirth original = BirthFixtures.pune1985();

        store.save(userId, "Asha", "Pune, India", original);
        StoredBirth reloaded = store.load(userId).orElseThrow();

        assertThat(reloaded.name()).isEqualTo("Asha");
        assertThat(reloaded.placeQuery()).isEqualTo("Pune, India");
        assertThat(reloaded.birthData()).isEqualTo(original.birthData());

        ResolvedBirth r = reloaded.resolved();
        assertThat(r.instant()).isEqualTo(original.instant());
        assertThat(r.zoneId()).isEqualTo(original.zoneId());
        assertThat(r.offsetApplied()).isEqualTo(original.offsetApplied());
        assertThat(r.latitude()).isEqualTo(original.latitude());
        assertThat(r.longitude()).isEqualTo(original.longitude());
        assertThat(r.birthTimeKnown()).isTrue();
        assertThat(r.flags()).isEmpty();
        assertThat(r.versions()).isEqualTo(original.versions());
    }

    @Test
    void aPre1970BirthRoundTripsToTheExactInstant() {
        long userId = newUser("+911111100005");
        ResolvedBirth original = BirthFixtures.kolkata1943(); // 1943 — below the TIMESTAMP floor

        store.save(userId, "Kamala", "Calcutta", original);
        ResolvedBirth r = store.load(userId).orElseThrow().resolved();

        assertThat(r.instant()).isEqualTo(Instant.parse("1943-06-01T03:30:00Z"));
        assertThat(r.offsetApplied()).hasToString("+06:30");
        assertThat(store.load(userId).orElseThrow().birthData()).isEqualTo(original.birthData());
    }

    @Test
    void unknownTimeAndFlagsRoundTrip() {
        long userId = newUser("+911111100002");
        ResolvedBirth original = BirthFixtures.puneUnknownTime();

        store.save(userId, null, "Pune", original);
        ResolvedBirth r = store.load(userId).orElseThrow().resolved();

        assertThat(r.birthTimeKnown()).isFalse();
        assertThat(r.flags()).containsExactly(com.celestia.geo.BirthMomentFlag.TIME_NOT_KNOWN);
        assertThat(r.instant()).isEqualTo(original.instant());
    }

    @Test
    void zoneConflictAndStatedZoneRoundTripTogether() {
        long userId = newUser("+911111100003");
        ResolvedBirth original = BirthFixtures.karachiZoneConflict();

        store.save(userId, "Bilal", "Karachi", original);
        ResolvedBirth r = store.load(userId).orElseThrow().resolved();

        assertThat(r.flags()).contains(com.celestia.geo.BirthMomentFlag.ZONE_CONFLICT);
        assertThat(r.statedZoneId()).hasToString("Asia/Kolkata");
        assertThat(r.zoneId()).hasToString("Asia/Karachi");
    }

    @Test
    void resavingReplacesTheSingleRow() {
        long userId = newUser("+911111100004");
        store.save(userId, "A", "q1", BirthFixtures.pune1985());
        store.save(userId, "B", "q2", BirthFixtures.puneUnknownTime());

        assertThat(repo.findAll().stream().filter(e -> e.getUserId().equals(userId)).count())
                .isEqualTo(1);
        assertThat(store.load(userId).orElseThrow().name()).isEqualTo("B");
    }
}
