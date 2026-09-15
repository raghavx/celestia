package com.celestia.agent.persistence.store;

import com.celestia.agent.persistence.repository.AppUserRepository;
import com.celestia.channel.whatsapp.WindowPolicy;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SPEC-009: the WhatsApp 24h customer-service window, per user. Kept separate
 * from {@link UserAccountService} — identity/consent/forgetting is one
 * concern, the channel window is another.
 */
@Service
public class WhatsAppWindowStore {

    private final AppUserRepository repo;

    public WhatsAppWindowStore(AppUserRepository repo) {
        this.repo = repo;
    }

    /** Refreshes the window from a just-received inbound message. */
    @Transactional
    public void recordInbound(long userId, Instant when) {
        repo.updateWindowExpiresAt(userId, WindowPolicy.windowExpiresAt(when));
    }

    @Transactional(readOnly = true)
    public Optional<Instant> windowExpiresAt(long userId) {
        return repo.findById(userId).map(com.celestia.agent.persistence.entity.AppUserEntity::getWindowExpiresAt);
    }
}
