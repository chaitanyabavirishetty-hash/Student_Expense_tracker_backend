package com.ledger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Expense(
        String id,
        double amount,
        String category,
        String date,
        String note,
        String createdAt) {
}
