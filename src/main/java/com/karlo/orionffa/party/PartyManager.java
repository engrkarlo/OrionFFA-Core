package com.karlo.orionffa.party;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PartyManager {
    private final int maxSize;
    private final Duration inviteDuration;
    private final Map<UUID, Party> byMember = new HashMap<>();

    public PartyManager(int maxSize, Duration inviteDuration) {
        this.maxSize = maxSize;
        this.inviteDuration = inviteDuration;
    }

    public Optional<Party> find(UUID playerId) { return Optional.ofNullable(byMember.get(playerId)); }

    public PartyResult create(UUID playerId) {
        if (byMember.containsKey(playerId)) return PartyResult.fail("You are already in a party.");
        byMember.put(playerId, new Party(playerId));
        return PartyResult.ok();
    }

    public PartyResult invite(UUID inviter, UUID invited) {
        Party party = byMember.get(inviter);
        if (party == null) return PartyResult.fail("Create a party first.");
        if (party.activeMatch() != null) return PartyResult.fail("The party is currently in a match.");
        if (!party.leader().equals(inviter)) return PartyResult.fail("Only the party leader can invite players.");
        if (inviter.equals(invited)) return PartyResult.fail("You cannot invite yourself.");
        if (byMember.containsKey(invited)) return PartyResult.fail("That player is already in a party.");
        if (party.members().size() >= maxSize) return PartyResult.fail("Your party is full.");
        party.invites().put(invited, Instant.now().plus(inviteDuration));
        return PartyResult.ok();
    }

    public PartyResult join(UUID playerId, UUID leaderId) {
        if (byMember.containsKey(playerId)) return PartyResult.fail("You are already in a party.");
        Party party = byMember.get(leaderId);
        if (party == null || !party.leader().equals(leaderId)) return PartyResult.fail("That party no longer exists.");
        if (party.activeMatch() != null) return PartyResult.fail("The party is currently in a match.");
        Instant expiresAt = party.invites().remove(playerId);
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) return PartyResult.fail("You do not have an active invite to that party.");
        if (party.members().size() >= maxSize) return PartyResult.fail("That party is full.");
        party.members().add(playerId);
        byMember.put(playerId, party);
        return PartyResult.ok();
    }

    public PartyResult kick(UUID leaderId, UUID targetId) {
        Party party = byMember.get(leaderId);
        if (party == null || !party.leader().equals(leaderId)) return PartyResult.fail("Only the party leader can kick players.");
        if (party.activeMatch() != null) return PartyResult.fail("The party is currently in a match.");
        if (leaderId.equals(targetId) || !party.members().contains(targetId)) return PartyResult.fail("That player is not a removable party member.");
        party.members().remove(targetId);
        byMember.remove(targetId);
        return PartyResult.ok();
    }

    public PartyResult promote(UUID leaderId, UUID targetId) {
        Party party = byMember.get(leaderId);
        if (party == null || !party.leader().equals(leaderId)) return PartyResult.fail("Only the party leader can promote players.");
        if (party.activeMatch() != null) return PartyResult.fail("The party is currently in a match.");
        if (!party.members().contains(targetId)) return PartyResult.fail("That player is not in your party.");
        party.leader(targetId);
        return PartyResult.ok();
    }

    public PartyResult leave(UUID playerId) {
        Party party = byMember.get(playerId);
        if (party == null) return PartyResult.fail("You are not in a party.");
        if (party.activeMatch() != null) return PartyResult.fail("The party is currently in a match.");
        party.members().remove(playerId);
        byMember.remove(playerId);
        if (party.members().isEmpty()) return PartyResult.ok();
        if (party.leader().equals(playerId)) party.leader(party.members().getFirst());
        return PartyResult.ok();
    }

    public PartyResult disband(UUID leaderId) {
        Party party = byMember.get(leaderId);
        if (party == null || !party.leader().equals(leaderId)) return PartyResult.fail("Only the party leader can disband the party.");
        if (party.activeMatch() != null) return PartyResult.fail("The party is currently in a match.");
        party.members().forEach(byMember::remove);
        party.members().clear();
        party.invites().clear();
        return PartyResult.ok();
    }

    public Optional<Party> party(UUID playerId) { return Optional.ofNullable(byMember.get(playerId)); }

    public Optional<Party> byId(UUID partyId) {
        return byMember.values().stream().filter(p -> p.id().equals(partyId)).findFirst();
    }

    public boolean setActiveMatch(UUID playerId, UUID matchId) {
        Party party = byMember.get(playerId);
        if (party == null || party.activeMatch() != null) return false;
        party.activeMatch(matchId);
        return true;
    }

    public void clearActiveMatch(UUID partyId, UUID matchId) {
        byId(partyId).ifPresent(p -> { if (matchId.equals(p.activeMatch())) p.activeMatch(null); });
    }

    public Optional<Collection<UUID>> toggleChat(UUID playerId) {
        Party party = byMember.get(playerId);
        return party == null ? Optional.empty() : Optional.of(List.copyOf(party.members()));
    }

    public boolean toggleChatState(UUID playerId) {
        Party party = byMember.get(playerId);
        return party != null && party.toggleChat();
    }

    public boolean partyChatEnabled(UUID playerId) {
        Party party = byMember.get(playerId);
        return party != null && party.chatEnabled();
    }

    public List<UUID> recipients(UUID playerId) {
        Party party = byMember.get(playerId);
        return party == null ? List.of() : List.copyOf(party.members());
    }

    public int activeCount() {
        return (int) byMember.values().stream().distinct().count();
    }

    public void remove(UUID playerId) {
        leave(playerId);
    }
}
