package com.karlo.orionffa.config;

import java.time.Duration;

public record RuntimeConfig(
        boolean ffaEnabled,
        LocationConfig lobby,
        LocationConfig editKit,
        Duration combatTag,
        Duration killerCredit,
        int partyMaxSize,
        Duration partyInviteDuration,
        ArenaSelectionMode selectionMode,
        ArenaSelectionStrategy selectionStrategy,
        boolean debug
) {
}
