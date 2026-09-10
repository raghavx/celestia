package com.celestia.agent.persistence.store;

import com.celestia.agent.persistence.entity.AppUserEntity;
import com.celestia.agent.persistence.repository.AppUserRepository;
import com.celestia.agent.persistence.repository.BirthDataRepository;
import com.celestia.agent.persistence.repository.ChartRepository;
import com.celestia.agent.persistence.repository.PredictionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identity: find-or-create by verified phone (race-safe), write-once consent, and
 * the "forget me" hard-delete (SPEC-008 US3; ADR-0016).
 */
@Service
public class UserAccountService {

    private final AppUserRepository users;
    private final BirthDataRepository births;
    private final ChartRepository charts;
    private final PredictionRepository predictions;

    public UserAccountService(AppUserRepository users, BirthDataRepository births,
            ChartRepository charts, PredictionRepository predictions) {
        this.users = users;
        this.births = births;
        this.charts = charts;
        this.predictions = predictions;
    }

    /**
     * The existing user for {@code phoneE164}, or a new one. Idempotent under
     * concurrency — a lost race on the unique key is caught and re-read, so no
     * caller sees a {@link DataIntegrityViolationException} (SC-003).
     */
    public AppUserRef findOrCreate(String phoneE164) {
        return users.findByPhoneE164(phoneE164)
                .map(u -> new AppUserRef(u.getId(), u.getPhoneE164(), false))
                .orElseGet(() -> create(phoneE164));
    }

    // Not @Transactional: saveAndFlush runs in its own unit of work, so a lost
    // race on the unique key throws without poisoning an ambient transaction,
    // and the follow-up read sees the winner's row.
    private AppUserRef create(String phoneE164) {
        try {
            AppUserEntity saved = users.saveAndFlush(new AppUserEntity(phoneE164));
            return new AppUserRef(saved.getId(), saved.getPhoneE164(), true);
        } catch (DataIntegrityViolationException race) {
            AppUserEntity existing = users.findByPhoneE164(phoneE164).orElseThrow(() -> race);
            return new AppUserRef(existing.getId(), existing.getPhoneE164(), false);
        }
    }

    /** Sets {@code consent_at} to now only if it is null; a later call is a no-op. */
    @Transactional
    public void recordConsent(long userId) {
        AppUserEntity u = users.findById(userId).orElseThrow();
        if (u.getConsentAt() == null) {
            u.setConsentAt(Instant.now());
            users.save(u);
        }
    }

    /**
     * "Forget me": delete the user's PII rows and anonymise {@code app_user}
     * (SC-004). Payment/tax rows are exempt (ADR-0016; not in this schema yet).
     * Deleting the user's {@code inbound_event} / {@code outbox_message} payload
     * rows is SPEC-009's extension.
     */
    @Transactional
    public void forget(long userId) {
        AppUserEntity u = users.findById(userId).orElseThrow();
        predictions.deleteByUserId(userId);
        charts.deleteByUserId(userId);   // dasha_period / horary_query cascade via FK
        births.deleteByUserId(userId);
        u.setPhoneHash(u.getPhoneE164() == null ? u.getPhoneHash() : sha256(u.getPhoneE164()));
        u.setPhoneE164(null);
        u.setDisplayName(null);
        u.setWaId(null);
        u.setDeletedAt(Instant.now());
        users.save(u);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
