package com.asie.aegisvault.admin;

import com.asie.aegisvault.User.AccountStatus;
import com.asie.aegisvault.User.Position;

import java.time.LocalDateTime;

/** 사용자 관리 화면에 비밀번호 해시나 변경 가능한 엔티티를 전달하지 않습니다. */
public record UserListItem(Long id, String nickname, String email, Position position,
                           AccountStatus accountStatus, Long departmentId, String departmentName,
                           LocalDateTime createdAt) { }
