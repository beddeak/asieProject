package com.asie.aegisvault.project;

public record Candidate(
    Long id, String nickname, com.asie.aegisvault.User.Position position, String department) {}
