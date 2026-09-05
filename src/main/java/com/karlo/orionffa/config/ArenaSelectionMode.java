package com.karlo.orionffa.config;

public enum ArenaSelectionMode {
    AUTOMATIC, GUI;

    public static ArenaSelectionMode parse(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AUTOMATIC;
        }
    }
}
