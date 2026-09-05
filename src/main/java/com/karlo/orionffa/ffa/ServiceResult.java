package com.karlo.orionffa.ffa;

import java.util.Map;

public record ServiceResult(boolean success, String messageKey, Map<String, String> placeholders) {
    public static ServiceResult ok(String key) { return new ServiceResult(true, key, Map.of()); }
    public static ServiceResult ok(String key, Map<String, String> placeholders) { return new ServiceResult(true, key, placeholders); }
    public static ServiceResult fail(String key) { return new ServiceResult(false, key, Map.of()); }
}
