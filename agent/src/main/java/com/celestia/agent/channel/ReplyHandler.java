package com.celestia.agent.channel;

import com.celestia.agent.persistence.store.AppUserRef;
import com.celestia.channel.whatsapp.InboundMessage;
import com.celestia.channel.whatsapp.OutboundMessage;

/**
 * Produces a reply for one inbound message. v1's only implementation is
 * {@link EchoReplyHandler} (a canned acknowledgement); SPEC-010's onboarding
 * FSM and later SPEC-014/015's guarded LLM pipeline replace it without any
 * change to the webhook, the worker's claim/enqueue loop, or the sender.
 *
 * <p>MUST NOT throw for an unrecognised {@link com.celestia.channel.whatsapp.MessageContent}
 * — always return some {@link OutboundMessage} (FR-006).
 */
public interface ReplyHandler {

    OutboundMessage reply(AppUserRef user, InboundMessage inbound);
}
