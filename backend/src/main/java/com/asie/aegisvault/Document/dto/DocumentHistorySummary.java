package com.asie.aegisvault.Document.dto;

/** Only versions visible to the current reader are included. */
public record DocumentHistorySummary(
    DocumentListItem latestVisible, DocumentListItem latestApproved, boolean archived) {}
