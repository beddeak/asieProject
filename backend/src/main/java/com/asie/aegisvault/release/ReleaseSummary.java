package com.asie.aegisvault.release;

import java.time.Instant;

public record ReleaseSummary(
    Long id, int releaseNumber, String releasedBy, Instant releasedAt, Instant recalledAt) {}
