package com.pfe.sageline.service.workflow;

import java.util.List;

public record RuleVerdict(boolean allowed, List<String> blockingReasons) {
    public static RuleVerdict allow() { return new RuleVerdict(true, List.of()); }
    public static RuleVerdict block(String... reasons) { return new RuleVerdict(false, List.of(reasons)); }
}
