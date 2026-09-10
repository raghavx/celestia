package com.celestia.agent.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** {@code app_user} — the verified phone number is the identity (Constitution VII). */
@Entity
@Table(name = "app_user")
public class AppUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_e164", unique = true, length = 20)
    private String phoneE164;

    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    @Column(name = "wa_id", length = 32)
    private String waId;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(length = 16)
    private String locale;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "onboarding_state", nullable = false, length = 32)
    private String onboardingState = "NEW";

    @Column(name = "guardrail_strikes", nullable = false)
    private int guardrailStrikes = 0;

    @Column(name = "cooldown_until")
    private Instant cooldownUntil;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected AppUserEntity() {}

    public AppUserEntity(String phoneE164) {
        this.phoneE164 = phoneE164;
    }

    public Long getId() {
        return id;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public void setPhoneE164(String phoneE164) {
        this.phoneE164 = phoneE164;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public String getWaId() {
        return waId;
    }

    public void setWaId(String waId) {
        this.waId = waId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Instant getConsentAt() {
        return consentAt;
    }

    public void setConsentAt(Instant consentAt) {
        this.consentAt = consentAt;
    }

    public String getOnboardingState() {
        return onboardingState;
    }

    public void setOnboardingState(String onboardingState) {
        this.onboardingState = onboardingState;
    }

    public int getGuardrailStrikes() {
        return guardrailStrikes;
    }

    public Instant getCooldownUntil() {
        return cooldownUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
