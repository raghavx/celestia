package com.celestia.agent.persistence.store;

import com.celestia.agent.persistence.entity.GeocodeCacheEntity;
import com.celestia.agent.persistence.json.GeocodeResultCodec;
import com.celestia.agent.persistence.repository.GeocodeCacheRepository;
import com.celestia.geo.place.GeocodeCache;
import com.celestia.geo.place.GeocodeResult;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The persistent implementation of SPEC-007's {@link GeocodeCache} port, backed
 * by {@code geocode_cache} (unique on {@code query_norm}). A hit returns a
 * {@link GeocodeResult} whose candidate list equals the stored one (SC-005).
 */
@Component
public class JpaGeocodeCache implements GeocodeCache {

    private final GeocodeCacheRepository repo;
    private final GeocodeResultCodec codec;

    public JpaGeocodeCache(GeocodeCacheRepository repo, GeocodeResultCodec codec) {
        this.repo = repo;
        this.codec = codec;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GeocodeResult> get(String queryNorm) {
        return repo.findByQueryNorm(queryNorm).flatMap(e -> codec.read(e.getResultsJson()));
    }

    @Override
    @Transactional
    public void put(String queryNorm, GeocodeResult result) {
        String json = codec.write(queryNorm, result);
        GeocodeCacheEntity entity = repo.findByQueryNorm(queryNorm)
                .orElseGet(() -> new GeocodeCacheEntity(queryNorm, json));
        entity.setResultsJson(json);
        try {
            repo.save(entity);
        } catch (DataIntegrityViolationException race) {
            GeocodeCacheEntity existing = repo.findByQueryNorm(queryNorm).orElseThrow(() -> race);
            existing.setResultsJson(json);
            repo.save(existing);
        }
    }
}
