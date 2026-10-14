package com.asie.aegisvault.workflow;

import java.time.Instant;

public record QualityRunOption(Long id, Long retestOf, String testerName, Instant createdAt) {}
