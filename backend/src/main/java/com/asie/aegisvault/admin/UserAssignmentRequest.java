package com.asie.aegisvault.admin;

import com.asie.aegisvault.User.Position;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UserAssignmentRequest(
        @NotNull(message = "직급을 선택해주세요.") Position position,
        @Positive(message = "올바른 부서를 선택해주세요.") Long departmentId) {
}
