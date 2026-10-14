package com.asie.aegisvault.Document.dto;

public record DocumentHistorySummary(
    DocumentListItem latestVisible, DocumentListItem latestApproved, boolean archived) {}
