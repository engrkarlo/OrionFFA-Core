package com.karlo.orionffa.party;

public record PartyResult(boolean success, String reason) {
    public static PartyResult ok() { return new PartyResult(true, ""); }
    public static PartyResult fail(String reason) { return new PartyResult(false, reason); }
}
