package io.github.pulpogato.common;

/**
 * Marker interface implemented by every webhook event payload type: the single-event body classes
 * and the sealed supertypes generated for multi-event subcategories.
 */
public interface WebhookEvent extends PulpogatoType {}
