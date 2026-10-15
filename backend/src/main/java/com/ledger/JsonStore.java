package com.ledger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stores expenses and the budget in plain JSON files (expenses.json, budget.json)
 * in the working directory, so no database is needed.
 */
@Component
public class JsonStore {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path expensesFile = Path.of("expenses.json");
    private final Path budgetFile = Path.of("budget.json");

    public synchronized List<Expense> readExpenses() {
        try {
            if (!Files.exists(expensesFile)) {
                Files.writeString(expensesFile, "[]");
                return new ArrayList<>();
            }
            return mapper.readValue(expensesFile.toFile(), new TypeReference<List<Expense>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized void writeExpenses(List<Expense> expenses) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(expensesFile.toFile(), expenses);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized double readBudget() {
        try {
            if (!Files.exists(budgetFile)) {
                Files.writeString(budgetFile, "{\"amount\": 0}");
                return 0;
            }
            JsonNode node = mapper.readTree(budgetFile.toFile());
            return node.path("amount").asDouble(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized void writeBudget(double amount) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(budgetFile.toFile(), Map.of("amount", amount));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
