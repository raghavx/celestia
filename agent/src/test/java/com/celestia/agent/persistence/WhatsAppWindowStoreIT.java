package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.persistence.store.UserAccountService;
import com.celestia.agent.persistence.store.WhatsAppWindowStore;
import com.celestia.channel.whatsapp.WindowPolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** SPEC-009 US3 — the 24h window store. */
@Transactional
class WhatsAppWindowStoreIT extends PersistenceIT {

    @Autowired
    WhatsAppWindowStore windowStore;

    @Autowired
    UserAccountService users;

    @Test
    void recordInboundSetsTheWindowToTwentyFourHoursOut() {
        long id = users.findOrCreate("+919888800001").id();
        Instant when = Instant.parse("2026-01-01T00:00:00Z");

        windowStore.recordInbound(id, when);

        assertThat(windowStore.windowExpiresAt(id)).contains(WindowPolicy.windowExpiresAt(when));
    }

    @Test
    void anAbsentUserHasNoWindow() {
        assertThat(windowStore.windowExpiresAt(999_999L)).isEmpty();
    }

    @Test
    void aFreshUserHasNoWindowUntilTheyMessage() {
        long id = users.findOrCreate("+919888800002").id();
        assertThat(windowStore.windowExpiresAt(id)).isEmpty();
    }
}
