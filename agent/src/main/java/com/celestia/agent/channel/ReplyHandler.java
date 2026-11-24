package com.celestia.agent.channel;

import com.celestia.agent.persistence.store.AppUserRef;
import com.celestia.channel.whatsapp.InboundMessage;
import com.celestia.channel.whatsapp.OutboundMessage;

/**
 * Produces a reply for one inbound message. The implementation is
 * {@link GuardedReplyHandler} (SPEC-012): entitlement, then the turn permit, then
 * SPEC-011's guarded path to the model. It replaced v1's canned
 * acknowledgement with no change to the webhook, the worker's claim/enqueue
 * loop, or the sender — which is what this interface existed to make possible.
 *
 * <p><b>There is exactly one implementation, and that is the point</b>
 * (Constitution V). A second reply path in the code is the thing SPEC-012 exists
 * to prevent, so the echo was deleted rather than kept behind a flag.
 *
 * <p>MUST NOT throw for an unrecognised {@link com.celestia.channel.whatsapp.MessageContent}
 * — always return some {@link OutboundMessage} (FR-006).
 */
public interface ReplyHandler {

    OutboundMessage reply(AppUserRef user, InboundMessage inbound);
}
