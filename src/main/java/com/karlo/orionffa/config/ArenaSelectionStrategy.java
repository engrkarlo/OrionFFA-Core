package com.karlo.orionffa.config;

public enum ArenaSelectionStrategy {
    FIRST_AVAILABLE, LEAST_OCCUPIED, RANDOM;

    public static ArenaSelectionStrategy parse(String value) {
        return switch (value.toLowerCase()) {
            case "first-available" -> FIRST_AVAILABLE;
            case "random" -> RANDOM;
            default -> LEAST_OCCUPIED;
        };
    }
}
