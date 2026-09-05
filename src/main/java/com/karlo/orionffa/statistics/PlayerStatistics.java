package com.karlo.orionffa.statistics;

public record PlayerStatistics(int kills, int deaths, int killStreak, int bestKillStreak) {
    public static final PlayerStatistics EMPTY = new PlayerStatistics(0, 0, 0, 0);

    public PlayerStatistics kill() {
        int nextStreak = killStreak + 1;
        return new PlayerStatistics(kills + 1, deaths, nextStreak, Math.max(bestKillStreak, nextStreak));
    }

    public PlayerStatistics death() {
        return new PlayerStatistics(kills, deaths + 1, 0, bestKillStreak);
    }

    public double kd() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }
}
