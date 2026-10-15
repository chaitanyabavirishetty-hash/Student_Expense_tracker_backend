package com.ledger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final List<String> CATEGORIES = List.of(
            "Food", "Transport", "Shopping", "Bills", "Entertainment", "Health", "Other");

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int STREAK_CAP = 90;

    private final JsonStore store;

    public ApiController(JsonStore store) {
        this.store = store;
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    private static LocalDate dayOf(Expense e) {
        return LocalDate.parse(e.date().substring(0, 10));
    }

    // ---------- categories ----------

    @GetMapping("/categories")
    public List<String> categories() {
        return CATEGORIES;
    }

    // ---------- budget ----------

    @GetMapping("/budget")
    public Map<String, Object> getBudget() {
        return Map.of("amount", store.readBudget());
    }

    @PutMapping("/budget")
    public ResponseEntity<?> setBudget(@RequestBody Map<String, Object> body) {
        double amount = parseAmount(body.get("amount"));
        if (!(amount > 0)) {
            return error(HttpStatus.BAD_REQUEST, "Budget must be a positive number");
        }
        store.writeBudget(amount);
        return ResponseEntity.ok(Map.of("amount", amount));
    }

    // ---------- expenses ----------

    @GetMapping("/expenses")
    public List<Expense> listExpenses() {
        return store.readExpenses().stream()
                .sorted(Comparator.comparing(ApiController::dayOf).reversed())
                .collect(Collectors.toList());
    }

    @PostMapping("/expenses")
    public ResponseEntity<?> addExpense(@RequestBody Map<String, Object> body) {
        double amount = parseAmount(body.get("amount"));
        if (!(amount > 0)) {
            return error(HttpStatus.BAD_REQUEST, "Amount must be a positive number");
        }

        String category = body.get("category") == null ? "" : body.get("category").toString();
        if (!CATEGORIES.contains(category)) {
            return error(HttpStatus.BAD_REQUEST, "Unrecognized category");
        }

        String date = body.get("date") == null ? "" : body.get("date").toString();
        try {
            LocalDate.parse(date.length() >= 10 ? date.substring(0, 10) : date);
        } catch (DateTimeParseException e) {
            return error(HttpStatus.BAD_REQUEST, "A valid date is required");
        }

        String note = body.get("note") == null ? "" : body.get("note").toString().trim();

        Expense expense = new Expense(
                UUID.randomUUID().toString(),
                amount,
                category,
                date,
                note,
                Instant.now().toString());

        List<Expense> expenses = store.readExpenses();
        expenses.add(expense);
        store.writeExpenses(expenses);

        return ResponseEntity.status(HttpStatus.CREATED).body(expense);
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<?> deleteExpense(@PathVariable String id) {
        List<Expense> expenses = store.readExpenses();
        List<Expense> remaining = expenses.stream()
                .filter(e -> !e.id().equals(id))
                .collect(Collectors.toList());

        if (remaining.size() == expenses.size()) {
            return error(HttpStatus.NOT_FOUND, "Expense not found");
        }
        store.writeExpenses(remaining);
        return ResponseEntity.noContent().build();
    }

    // ---------- summary: monthly total, by-category totals, no-spend streak ----------

    @GetMapping("/expenses/summary")
    public Map<String, Object> summary() {
        List<Expense> expenses = store.readExpenses();
        LocalDate today = LocalDate.now(ZONE);
        YearMonth thisMonth = YearMonth.from(today);

        List<Expense> monthExpenses = expenses.stream()
                .filter(e -> YearMonth.from(dayOf(e)).equals(thisMonth))
                .collect(Collectors.toList());

        double monthlyTotal = monthExpenses.stream().mapToDouble(Expense::amount).sum();

        List<Map<String, Object>> byCategory = new ArrayList<>();
        for (String category : CATEGORIES) {
            double total = monthExpenses.stream()
                    .filter(e -> e.category().equals(category))
                    .mapToDouble(Expense::amount)
                    .sum();
            if (total > 0) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("category", category);
                row.put("total", total);
                byCategory.add(row);
            }
        }
        byCategory.sort((a, b) -> Double.compare((double) b.get("total"), (double) a.get("total")));

        String monthLabel = today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + today.getYear();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("monthlyTotal", monthlyTotal);
        result.put("month", monthLabel);
        result.put("byCategory", byCategory);
        result.put("noSpendStreak", noSpendStreak(expenses, today));
        return result;
    }

    // Consecutive days, counting back from today, with no expenses logged (capped at 90).
    private int noSpendStreak(List<Expense> expenses, LocalDate today) {
        if (expenses.isEmpty()) return 0;

        Set<LocalDate> spendDays = expenses.stream()
                .map(ApiController::dayOf)
                .collect(Collectors.toSet());

        int streak = 0;
        LocalDate cursor = today;
        while (streak < STREAK_CAP && !spendDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private static double parseAmount(Object raw) {
        if (raw == null) return 0;
        try {
            double value = Double.parseDouble(raw.toString());
            return Double.isFinite(value) ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
