package com.karlo.orionffa.arena;

import com.karlo.orionffa.config.LocationConfig;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Minimal runnable guard for reservation exclusivity and explicit kit compatibility. */
public final class ArenaReservationCheck {
    public static void main(String[] args) {
        Arena arena = new Arena("test", new LocationConfig("world", 0, 0, 0, 0, 0), true, 1, null, Set.of("sword"), false, null, null);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assert arena.supports("sword");
        assert !arena.supports("nethpot");
        assert arena.reserve(first, Instant.now().plusSeconds(5));
        assert !arena.reserve(second, Instant.now().plusSeconds(5));
        assert arena.claim(first);
        arena.leave(first);
        assert arena.reserve(second, Instant.now().plusSeconds(5));
    }
}
