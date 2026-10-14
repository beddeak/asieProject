package com.asie.aegisvault.admin;

import com.asie.aegisvault.User.AccountStatus;
import com.asie.aegisvault.User.Position;
import java.time.LocalDateTime;

public record UserListItem(
    Long id,
    String nickname,
    String email,
    Position position,
    AccountStatus accountStatus,
    Long departmentId,
    String departmentName,
    LocalDateTime createdAt,
    com.asie.aegisvault.security.SecurityClassification clearance) {}
