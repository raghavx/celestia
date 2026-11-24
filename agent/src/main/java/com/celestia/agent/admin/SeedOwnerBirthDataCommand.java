package com.celestia.agent.admin;

import com.celestia.agent.persistence.store.AppUserRef;
import com.celestia.agent.persistence.store.BirthDataStore;
import com.celestia.agent.persistence.store.UserAccountService;
import com.celestia.geo.BirthMomentFlag;
import com.celestia.geo.DatasetVersions;
import com.celestia.geo.ResolvedBirth;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Seeds the owner's birth data by an administrative action (SPEC-012 FR-019).
 *
 * <p><b>No conversational onboarding.</b> Collecting name, date, time and place in
 * a dialogue is SPEC-017 and a deterministic flow under Constitution VI; the
 * preview has one user whose details are known, so they are configured and
 * written once.
 *
 * <p>Coordinates and a zone are given explicitly rather than geocoded. That is a
 * deliberate limit of this command: {@code geo} defines the geocoder port
 * (SPEC-007) but no implementation is wired into this application, so a place
 * <em>name</em> cannot be resolved yet. An administrator supplying coordinates is
 * not the model supplying them, so FR-006 is not in play here.
 */
@Component
public class SeedOwnerBirthDataCommand {

    private static final Logger log = LoggerFactory.getLogger(SeedOwnerBirthDataCommand.class);

    private final UserAccountService users;
    private final BirthDataStore births;

    public SeedOwnerBirthDataCommand(UserAccountService users, BirthDataStore births) {
        this.users = users;
        this.births = births;
    }

    /**
     * Finds or creates the user for {@code phoneE164} and stores their birth.
     *
     * @return the user the data was written for
     */
    public AppUserRef seed(OwnerSeed seed) {
        seed.validate();
        AppUserRef user = users.findOrCreate(seed.phoneE164());
        LocalTime time = seed.birthTime() == null ? LocalTime.NOON : LocalTime.parse(seed.birthTime());
        LocalDate date = LocalDate.parse(seed.birthDate());
        ZoneId zone = ZoneId.of(seed.zoneId());
        LocalDateTime local = LocalDateTime.of(date, time);
        ZoneOffset offset = zone.getRules().getOffset(local);

        ResolvedBirth resolved = new ResolvedBirth(
                local,
                zone,
                local.toInstant(offset),
                offset,
                seed.latitude(),
                seed.longitude(),
                seed.placeName(),
                seed.country(),
                "admin-seed",
                null,
                seed.birthTime() != null,
                seed.birthTime() != null
                        ? EnumSet.noneOf(BirthMomentFlag.class)
                        : EnumSet.of(BirthMomentFlag.TIME_NOT_KNOWN),
                new DatasetVersions("admin", "admin"));

        births.save(user.id(), seed.name(), seed.placeName(), resolved);
        log.info("seeded birth data for user {} (FR-019)", user.id());
        return user;
    }

    /**
     * The owner's details, from configuration.
     *
     * @param enabled whether to seed at startup; off unless asked for, so an
     *     ordinary boot never writes to {@code birth_data}
     */
    @ConfigurationProperties("celestia.seed-owner")
    public record OwnerSeed(
            boolean enabled,
            String phoneE164,
            String name,
            String birthDate,
            String birthTime,
            String zoneId,
            Double latitude,
            Double longitude,
            String placeName,
            String country) {

        void validate() {
            require(phoneE164, "celestia.seed-owner.phone-e164");
            require(birthDate, "celestia.seed-owner.birth-date");
            require(zoneId, "celestia.seed-owner.zone-id");
            require(placeName, "celestia.seed-owner.place-name");
            if (latitude == null || longitude == null) {
                throw new IllegalArgumentException(
                        "celestia.seed-owner.latitude and .longitude are required: no geocoder is "
                                + "wired, so a place name cannot be resolved to coordinates");
            }
        }

        private static void require(String value, String key) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(key + " is required to seed the owner's birth data");
            }
        }

    }

    /** Runs the seed at startup, only when explicitly asked for. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OwnerSeed.class)
    static class SeedRunnerConfig {

        @Bean
        ApplicationRunner seedOwnerBirthData(SeedOwnerBirthDataCommand command, OwnerSeed seed) {
            return (ApplicationArguments args) -> {
                if (!seed.enabled()) {
                    return;
                }
                command.seed(seed);
            };
        }
    }
}
