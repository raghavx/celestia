package com.celestia.agent.persistence.store;

import com.celestia.agent.persistence.entity.BirthDataEntity;
import com.celestia.agent.persistence.repository.BirthDataRepository;
import com.celestia.geo.BirthMomentFlag;
import com.celestia.geo.DatasetVersions;
import com.celestia.geo.ResolvedBirth;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the SPEC-007 {@link ResolvedBirth} as the one {@code birth_data} row
 * for a user and reloads it losslessly (SPEC-008 US1, SC-001).
 */
@Service
public class BirthDataStore {

    private final BirthDataRepository repo;

    public BirthDataStore(BirthDataRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void save(long userId, String name, String placeQuery, ResolvedBirth r) {
        BirthDataEntity e = repo.findByUserId(userId).orElseGet(BirthDataEntity::new);
        e.setUserId(userId);
        e.setName(name);
        e.setPlaceQuery(placeQuery);
        e.setBirthDate(r.localDateTime().toLocalDate());
        e.setBirthTime(r.birthTimeKnown() ? r.localDateTime().toLocalTime() : null);
        e.setBirthTimeKnown(r.birthTimeKnown());
        e.setPlaceName(r.placeLabel());
        e.setCountry(r.country());
        e.setLat(BigDecimal.valueOf(r.latitude()));
        e.setLon(BigDecimal.valueOf(r.longitude()));
        e.setZoneId(r.zoneId().getId());
        e.setBirthUtc(r.instant());
        e.setOffsetApplied(r.offsetApplied().getId());
        e.setGeocodeSource(r.geocodeSource());
        e.setStatedZoneId(r.statedZoneId() == null ? null : r.statedZoneId().getId());
        e.setFlags(r.flags().stream().map(Enum::name).collect(Collectors.joining(",")));
        e.setTzdbVersion(r.versions().tzdbVersion());
        e.setTzBoundaryVersion(r.versions().timezoneBoundaryVersion());
        repo.save(e);
    }

    @Transactional(readOnly = true)
    public Optional<StoredBirth> load(long userId) {
        return repo.findByUserId(userId).map(BirthDataStore::toStored);
    }

    private static StoredBirth toStored(BirthDataEntity e) {
        EnumSet<BirthMomentFlag> flags = EnumSet.noneOf(BirthMomentFlag.class);
        if (e.getFlags() != null && !e.getFlags().isBlank()) {
            for (String f : e.getFlags().split(",")) {
                flags.add(BirthMomentFlag.valueOf(f.trim()));
            }
        }
        LocalTime time = e.isBirthTimeKnown()
                ? (e.getBirthTime() != null ? e.getBirthTime() : LocalTime.NOON)
                : LocalTime.NOON;
        LocalDateTime local = LocalDateTime.of(e.getBirthDate(), time);
        ZoneId stated = e.getStatedZoneId() == null ? null : ZoneId.of(e.getStatedZoneId());

        ResolvedBirth resolved = new ResolvedBirth(
                local,
                ZoneId.of(e.getZoneId()),
                e.getBirthUtc(),
                ZoneOffset.of(e.getOffsetApplied()),
                e.getLat().doubleValue(),
                e.getLon().doubleValue(),
                e.getPlaceName(),
                e.getCountry(),
                e.getGeocodeSource(),
                stated,
                e.isBirthTimeKnown(),
                flags,
                new DatasetVersions(e.getTzdbVersion(), e.getTzBoundaryVersion()));
        return new StoredBirth(e.getName(), e.getPlaceQuery(), resolved);
    }
}
