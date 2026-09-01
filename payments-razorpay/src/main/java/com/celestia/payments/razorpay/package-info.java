/**
 * Razorpay adapter implementing the {@code PaymentGateway} port.
 *
 * <p>Payment-link creation, webhook signature verification on the raw body,
 * idempotent event handling, and server-side reconciliation. See SPEC-012.
 */
package com.celestia.payments.razorpay;
