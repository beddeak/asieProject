package com.asie.aegisvault.User.dto;

/** 홈페이지에 필요한 계정 정보만 전달합니다. */
public record HomeProfile(String nickname, String departmentName, String positionName) {
}
