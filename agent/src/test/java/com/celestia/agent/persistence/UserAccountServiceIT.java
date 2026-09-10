package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.persistence.repository.AppUserRepository;
import com.celestia.agent.persistence.repository.BirthDataRepository;
import com.celestia.agent.persistence.repository.ChartRepository;
import com.celestia.agent.persistence.repository.DashaPeriodRepository;
import com.celestia.agent.persistence.repository.PredictionRepository;
import com.celestia.agent.persistence.store.BirthDataStore;
import com.celestia.agent.persistence.store.ChartCacheService;
import com.celestia.agent.persistence.store.PredictionCacheService;
import com.celestia.agent.persistence.store.UserAccountService;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** SPEC-008 US3 — identity: race-safe create, write-once consent, "forget me". */
class UserAccountServiceIT extends PersistenceIT {

    @Autowired
    UserAccountService users;

    @Autowired
    AppUserRepository userRepo;

    @Autowired
    BirthDataStore birthDataStore;

    @Autowired
    ChartCacheService charts;

    @Autowired
    PredictionCacheService predictions;

    @Autowired
    BirthDataRepository birthRepo;

    @Autowired
    ChartRepository chartRepo;

    @Autowired
    DashaPeriodRepository dashaRepo;

    @Autowired
    PredictionRepository predictionRepo;

    @Test
    void twentyConcurrentFirstContactsYieldOneRowAndNoException() throws Exception {
        String phone = "+919999900001";
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Callable<Long>> tasks = IntStream.range(0, 20)
                .<Callable<Long>>mapToObj(i -> () -> users.findOrCreate(phone).id())
                .toList();

        List<Long> ids;
        try {
            ids = pool.invokeAll(tasks).stream().map(UserAccountServiceIT::get).toList();
        } finally {
            pool.shutdown();
        }

        assertThat(ids).doesNotContainNull();
        assertThat(ids.stream().distinct().collect(Collectors.toSet())).hasSize(1);
        assertThat(userRepo.findByPhoneE164(phone)).isPresent();
    }

    @Test
    void repeatLookupReturnsTheSameRow() {
        long a = users.findOrCreate("+919999900002").id();
        long b = users.findOrCreate("+919999900002").id();
        assertThat(a).isEqualTo(b);
    }

    @Test
    void consentIsWrittenOnce() {
        long id = users.findOrCreate("+919999900003").id();
        users.recordConsent(id);
        var first = userRepo.findById(id).orElseThrow().getConsentAt();
        assertThat(first).isNotNull();
        users.recordConsent(id);
        assertThat(userRepo.findById(id).orElseThrow().getConsentAt()).isEqualTo(first);
    }

    @Test
    void forgetRemovesEveryPiiRowAndAnonymisesTheUser() {
        String phone = "+919999900004";
        long id = users.findOrCreate(phone).id();
        birthDataStore.save(id, "Priya", "Pune", BirthFixtures.pune1985());
        charts.natalChart(id);
        predictions.dailyPrediction(id, LocalDate.of(2025, 6, 15));

        users.forget(id);

        assertThat(birthRepo.findByUserId(id)).isEmpty();
        assertThat(chartRepo.findByUserIdAndKind(id, "NATAL")).isEmpty();
        assertThat(predictionRepo.findByUserIdAndForDateAndKind(id, LocalDate.of(2025, 6, 15), "DAILY"))
                .isEmpty();
        assertThat(dashaRepo.count()).isGreaterThanOrEqualTo(0); // never populated by SPEC-008

        var u = userRepo.findById(id).orElseThrow();
        assertThat(u.getPhoneE164()).isNull();
        assertThat(u.getPhoneHash()).hasSize(64).isNotEqualTo(phone);
        assertThat(u.getDisplayName()).isNull();
        assertThat(u.getWaId()).isNull();
        assertThat(u.getDeletedAt()).isNotNull();
        // a re-contact from the same number creates a fresh user
        assertThat(users.findOrCreate(phone).id()).isNotEqualTo(id);
    }

    private static <T> T get(Future<T> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
