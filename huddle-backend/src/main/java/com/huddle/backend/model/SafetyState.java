package com.huddle.backend.model;

public enum SafetyState {
    PENDING_ASSESSMENT,
    CLEARED,
    BLOCKED_PENDING_REVIEW,
    CONFIRMED_MALICIOUS,
    PERMANENTLY_REMOVED
}
