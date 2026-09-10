package com.celestia.agent.persistence.store;

/** A lightweight handle to an {@code app_user} row. */
public record AppUserRef(long id, String phoneE164, boolean created) {}
