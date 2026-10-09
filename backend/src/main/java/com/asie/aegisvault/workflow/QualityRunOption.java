package com.asie.aegisvault.workflow;

import java.time.Instant;

/** 선택 목록에는 시험 근거 본문을 가져오지 않습니다. */
public record QualityRunOption(Long id, Long retestOf, String testerName, Instant createdAt) {}
